"""Managed worker entry point. Import only stdlib and the pure guard until approved."""
import importlib
import logging
import os
from pathlib import Path
import re
import sys
from urllib.parse import quote, quote_plus

from worker_launch_guard import LaunchDenied, authorize


class _RedactingFormatter(logging.Formatter):
    def __init__(self, secrets):
        super().__init__("%(asctime)s [%(levelname)s] %(name)s: %(message)s")
        self._secrets = sorted({spelling for secret in secrets if secret
                                for spelling in (secret, quote(secret, safe=""), quote_plus(secret))},
                               key=len, reverse=True)

    def format(self, record):
        rendered = super().format(record)
        for secret in self._secrets:
            rendered = rendered.replace(secret, "[REDACTED]")
        return re.sub(r"\b[a-zA-Z][a-zA-Z0-9+.-]*://[^\s]+", "[REDACTED_URI]", rendered)


def _run_worker(plan):
    """Fresh, dedicated worker process only; API settings/code are not changed."""
    if any(name in sys.modules for name in ("app.core.config", "app.database.session", "app.kafka.worker")):
        raise RuntimeError("RUNTIME_ALREADY_IMPORTED")
    # Do not let ambient API credentials, debug flags or unrelated Settings leak in.
    platform = {key: os.environ[key] for key in ("PATH", "SYSTEMROOT", "WINDIR", "LANG", "LC_ALL")
                if key in os.environ}
    os.environ.clear()
    os.environ.update(platform, **plan.settings)
    service = Path(__file__).resolve().parents[1]
    sys.path.insert(0, str(service))
    handler = logging.StreamHandler()
    handler.setFormatter(_RedactingFormatter((plan.settings["MYSQL_PASSWORD"], plan.settings["MYSQL_USER"])))
    root = logging.getLogger()
    root.handlers = [handler]
    root.setLevel(logging.INFO)
    # Handler/formatting diagnostics must not dump raw LogRecord arguments.
    logging.raiseExceptions = False
    # app/core/__init__.py and app/__init__.py only have docstrings. Definitions
    # are safe to load here, after authorization, before core.config constructs
    # Settings and database.session constructs its engine.
    definition = importlib.import_module("app.core.settings")
    definition.Settings.model_config["env_file"] = None
    importlib.import_module("app.kafka.worker").main()


def main(environment=None, *, read_file=None, now=None, runtime=None, stderr=None):
    output = stderr if stderr is not None else sys.stderr
    try:
        options = {} if read_file is None else {"read_file": read_file}
        plan = authorize(os.environ if environment is None else environment, now=now, **options)
        if plan is None:
            print("WORKER_DISABLED", file=output)
            return 0
    except LaunchDenied as denied:
        print(denied.reason, file=output)
        return denied.exit_code
    except Exception:
        print("LAUNCH_CONFIGURATION_FAILURE", file=output)
        return 78
    try:
        (runtime or _run_worker)(plan)
    except KeyboardInterrupt:
        print("WORKER_INTERRUPTED", file=output)
        return 130
    except BaseException:
        # No exception text/traceback, including SystemExit(0) from a dependency.
        print("WORKER_RUNTIME_FAILURE", file=output)
        return 70
    # Legacy main() returns None for both SIGTERM and protective stop. Until a
    # separate worker-control-flow task supplies a reliable stop reason, neither
    # is reported as successful completion by this managed entry point.
    print("WORKER_STOP_UNCLASSIFIED", file=output)
    return 70


if __name__ == "__main__":
    raise SystemExit(main())
