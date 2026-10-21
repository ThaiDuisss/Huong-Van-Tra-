"""Save Codex Markdown reports outside the project; never overwrite old reports.

Usage: python -B scripts/codex/save_report.py --task task-name < external-input.json
The JSON payload requires scope, changes, tests and issues; modified_files and
details are optional. Input may also be piped directly, without creating a draft.
"""

import argparse
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import re
import subprocess
import sys


PROJECT_ROOT = Path(__file__).resolve().parents[2]
BACKUP_ROOT = Path(r"E:\Huong-Van-Tra-Backup\codex-reports")
REPORT_TIMEZONE = timezone(timedelta(hours=7), "Asia/Bangkok")


def verify_destination(destination):
    resolved = destination.resolve()
    if resolved == PROJECT_ROOT or PROJECT_ROOT in resolved.parents:
        raise ValueError("Report destination must be outside the project")
    ancestor = resolved
    while not ancestor.exists():
        ancestor = ancestor.parent
    result = subprocess.run(
        ["git", "-C", str(ancestor), "rev-parse", "--show-toplevel"],
        capture_output=True, text=True, check=False,
    )
    if result.returncode == 0:
        # Explicit user-approved exception: the backup inherits E:\.git, while
        # remaining outside the project repository. Never stage backup files.
        inherited_root = Path(result.stdout.strip()).resolve()
        configured_root = BACKUP_ROOT.resolve()
        if inherited_root == Path("E:/") and (
            resolved == configured_root or configured_root in resolved.parents
        ):
            return resolved
        raise ValueError(
            f"Report destination {resolved} is inside Git worktree {result.stdout.strip()}"
        )
    if result.returncode != 128 or "not a git repository" not in result.stderr.lower():
        raise RuntimeError("Could not verify that the report destination is outside Git")
    return resolved


def write_unique(directory, stem, content):
    """Exclusive creation is safe even if two report writers choose the same name."""
    for suffix in range(100000):
        name = stem if suffix == 0 else f"{stem}-{suffix + 1}"
        candidate = directory / f"{name}.md"
        try:
            with candidate.open("x", encoding="utf-8", newline="\n") as output:
                output.write(content)
            return candidate
        except FileExistsError:
            continue
    raise RuntimeError("Could not allocate a unique report filename")


def save_report(task_name, payload):
    if not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", task_name):
        raise ValueError("Task name must contain lowercase ASCII words separated by hyphens")
    if not isinstance(payload, dict):
        raise ValueError("Report input must be a JSON object")
    for key in ("scope", "changes", "tests", "issues"):
        if not isinstance(payload.get(key), str) or not payload[key].strip():
            raise ValueError(f"Missing nonempty report field: {key}")
    modified = payload.get("modified_files", [])
    if not isinstance(modified, list) or any(not isinstance(p, str) or not p.strip() for p in modified):
        raise ValueError("modified_files must be an array of nonempty paths")
    details = payload.get("details", "")
    if not isinstance(details, str):
        raise ValueError("details must be Markdown text")

    report_root = verify_destination(BACKUP_ROOT)
    report_root.mkdir(parents=True, exist_ok=True)
    now = datetime.now(REPORT_TIMEZONE)
    day = verify_destination(report_root / now.strftime("%Y-%m-%d"))
    if day.parent != report_root:
        raise ValueError("Report date directory resolves outside the configured report root")
    day.mkdir(exist_ok=True)
    modified_text = "\n".join(f"- {Path(p).resolve()}" for p in modified) or "Không có file được sửa."
    content = (
        f"# {task_name}\n\n"
        f"Thời điểm: {now.isoformat(timespec='seconds')} (Asia/Bangkok, UTC+07:00).\n\n"
        f"## Phạm vi kiểm tra\n\n{payload['scope']}\n\n"
        f"## Thay đổi đã thực hiện\n\n{payload['changes']}\n\n"
        f"## Kết quả test / kiểm tra\n\n{payload['tests']}\n\n"
        f"## Vấn đề còn tồn tại / giới hạn\n\n{payload['issues']}\n\n"
        f"## Đường dẫn file đã sửa\n\n{modified_text}\n"
    )
    if details.strip():
        content += f"\n## Chi tiết\n\n{details.rstrip()}\n"
    return write_unique(day, f"{now:%H%M%S}_{task_name}", content)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--task", required=True)
    args = parser.parse_args()
    sys.stdin.reconfigure(encoding="utf-8")
    sys.stdout.reconfigure(encoding="utf-8")
    try:
        result = save_report(args.task, json.load(sys.stdin))
    except (OSError, ValueError, RuntimeError) as error:
        parser.exit(1, f"Report was not saved: {error}\nNo in-project fallback was used.\n")
    print(result)


if __name__ == "__main__":
    main()
