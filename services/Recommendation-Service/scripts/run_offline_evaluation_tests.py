"""Run service and offline tests with dummy settings and no outbound service access."""
import os
from pathlib import Path
import sys
import traceback
import unittest
from tempfile import TemporaryDirectory
from unittest.mock import patch

SERVICE = Path(__file__).resolve().parents[1]
os.chdir(SERVICE)
sys.path.insert(0,str(SERVICE))
os.environ.update(MYSQL_HOST="127.0.0.1", MYSQL_PORT="1", MYSQL_DATABASE="isolated_offline_tests",
                  MYSQL_USER="fixture", MYSQL_PASSWORD="fixture", APP_ENVIRONMENT="test",
                  INTERNAL_REPLAY_BACKEND="memory", SPRING_FLYWAY_ENABLED="false",
                  SPRING_SQL_INIT_MODE="never", STORAGE_INITIALIZATION_ENABLED="false",
                  PYTHONDONTWRITEBYTECODE="1")


def guard(event,args):
    if event == "socket.connect":
        internal=any(f.name=="_fallback_socketpair" and f.filename.endswith("socket.py")
                     for f in traceback.extract_stack())
        if not (internal and args[1][0] in ("127.0.0.1","::1") and args[1][1]>=49152):
            raise RuntimeError("Outbound services are disabled for isolated tests")


if __name__ == "__main__":
    sys.addaudithook(guard)
    loader=unittest.TestLoader()
    suite=unittest.TestSuite([loader.discover("tests",pattern="test_*.py",top_level_dir="."),
                             loader.discover("offline_benchmark/tests",pattern="test_*.py",top_level_dir=".")])
    # Legacy exact-TFIDF tests reserve REPO/.tmp report fixtures. Redirect the
    # test-only REPO to an external temporary root; never recreate project .tmp.
    from offline_benchmark.runners import run_exact_tfidf_benchmark as exact_runner
    with TemporaryDirectory(prefix="hvt-offline-tests-") as temporary:
        (Path(temporary)/".tmp").mkdir()
        with patch.object(exact_runner,"REPO",Path(temporary)):
            result=unittest.TextTestRunner(verbosity=1).run(suite)
    raise SystemExit(not result.wasSuccessful())
