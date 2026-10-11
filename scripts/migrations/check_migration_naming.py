"""Validate new Flyway names against a Git baseline, without accessing any DB."""

import argparse
from datetime import datetime
from pathlib import Path
import re
import subprocess
import sys
import zlib


NAME = re.compile(r"V(?P<version>\d+(?:[._]\d+)*)__[^/]+\.sql\Z")
TIMESTAMP = re.compile(r"\d{14}\Z")
# Applied in file_db; absent from local HEAD, recovered from verified remote Git.
# This is a naming exception for genuine recovery, not a substitute for provenance.
APPLIED_RESTORATIONS = {
    "services/File-Service/src/main/resources/migration/V6__create_business_sequences.sql": -478525518,
}


def flyway_checksum(source: str) -> int:
    checksum = zlib.crc32("".join(source.removeprefix("\ufeff").splitlines()).encode("utf-8"))
    return checksum if checksum < 2**31 else checksum - 2**32


def verified_restorations(current, read_source, expected=APPLIED_RESTORATIONS):
    restored, errors = [], []
    for path, checksum in expected.items():
        if path not in current:
            continue
        if flyway_checksum(read_source(path)) == checksum:
            restored.append(path)
        else:
            errors.append(f"Recovered applied migration checksum does not match verified history: {path}")
    return restored, errors


def is_migration(path: str) -> bool:
    return ("/migration/" in path or "/flyway/sql/" in path) and Path(path).name.startswith("V") and path.endswith(".sql")


def version_key(version: str) -> tuple[int, ...]:
    parts = [int(part) for part in re.split(r"[._]", version)]
    while len(parts) > 1 and parts[-1] == 0:
        parts.pop()
    return tuple(parts)


def migration_scope(path: str) -> str:
    # A service must not publish duplicate versions through different locations.
    parts = Path(path).parts
    if len(parts) >= 2 and parts[0] in {"services", "common", "infra"}:
        return "/".join(parts[:2])
    return str(Path(path).parent)


def validate(current: list[str], baseline: list[str]) -> list[str]:
    errors = []
    historical = set(baseline)
    maxima = {}
    for path in baseline:
        match = NAME.fullmatch(Path(path).name)
        if match:
            scope = migration_scope(path)
            maxima[scope] = max(maxima.get(scope, ()), version_key(match["version"]))

    seen = {}
    for path in sorted(current):
        match = NAME.fullmatch(Path(path).name)
        if not match:
            errors.append(f"Invalid migration filename: {path}")
            continue
        version = match["version"]
        scope = migration_scope(path)
        identity = (scope, version_key(version))
        if identity in seen:
            errors.append(f"Duplicate Flyway version in {scope}: {seen[identity]} and {path}")
        seen[identity] = path
        if path in historical:
            continue
        if not TIMESTAMP.fullmatch(version):
            errors.append(f"New migration requires VyyyyMMddHHmmss: {path}")
            continue
        try:
            timestamp = datetime.strptime(version, "%Y%m%d%H%M%S")
            if timestamp.strftime("%Y%m%d%H%M%S") != version:
                raise ValueError("Non-canonical timestamp")
        except ValueError:
            errors.append(f"Invalid calendar timestamp: {path}")
            continue
        if version_key(version) <= maxima.get(scope, ()):
            errors.append(f"New migration is not after the baseline history (outOfOrder=false): {path}")
    return errors


def git(root: Path, *args: str) -> list[str]:
    result = subprocess.run(
        ["git", "-C", str(root), *args], check=True, capture_output=True, encoding="utf-8"
    )
    return result.stdout.splitlines()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-ref", default="HEAD", help="Historical Git commit/branch; use the PR base commit in CI")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    try:
        commit = git(root, "rev-parse", "--verify", args.base_ref + "^{commit}")[0]
        baseline = [path for path in git(root, "ls-tree", "-r", "--name-only", commit) if is_migration(path)]
        candidates = git(root, "ls-files", "--cached", "--others", "--exclude-standard")
        current = sorted({path for path in candidates if is_migration(path) and (root / path).is_file()})
    except (subprocess.CalledProcessError, IndexError):
        print("FAIL: cannot resolve/read Git baseline", file=sys.stderr)
        return 1
    restored, errors = verified_restorations(current, lambda path: (root / path).read_text(encoding="utf-8-sig"))
    baseline.extend(restored)
    errors.extend(validate(current, baseline))
    for error in errors:
        print("FAIL:", error, file=sys.stderr)
    if errors:
        return 1
    print(f"PASS: {len(current)} migrations; {len(set(current) - set(baseline))} new timestamp migrations; no duplicate versions")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
