"""Ingredient V1 research entry point. Network and subprocess startup denied."""
from pathlib import Path
import sys


def offline_guard(event, args):
    if event in ("socket.connect", "subprocess.Popen"):
        raise RuntimeError("Ingredient research CLI is offline-only")
    if event == "open" and isinstance(args[0], str) and Path(args[0]).name == "evaluation.json":
        raise RuntimeError("latent evaluation sidecar is forbidden as model input")


if __name__ == "__main__":
    sys.dont_write_bytecode = True
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    sys.addaudithook(offline_guard)
    from app.ingredient_poc.recommendation.cli import main
    raise SystemExit(main())
