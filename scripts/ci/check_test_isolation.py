#!/usr/bin/env python3
"""Chặn test boot Spring context vào hạ tầng thật.

Mỗi class @SpringBootTest phải được cô lập theo một trong hai cách:
  1. @ActiveProfiles("test") + module có src/test/resources/application-test.yml, hoặc
  2. module có src/test/resources/application.yml (file này shadow hoàn toàn
     application.yml của main nên test không đọc được default trỏ DB dev).

Chạy: python -B scripts/ci/check_test_isolation.py
Thoát 0 nếu đạt, 1 nếu có class chưa cô lập. Script không kết nối mạng hay database.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MODULE_DIRS = ["services", "infra", "common"]
SPRING_BOOT_TEST = re.compile(r"^\s*@SpringBootTest\b", re.MULTILINE)
ACTIVE_PROFILES_TEST = re.compile(r'@ActiveProfiles\(\s*(\{\s*)?"test"')
# IP literal trong config test = test tro ra mot may that. Chi cho phep loopback.
IPV4 = re.compile(r"\b(?:\d{1,3}\.){3}\d{1,3}\b")
ALLOWED_IPS = {"127.0.0.1", "0.0.0.0", "255.255.255.255"}


def module_of(path: Path) -> Path | None:
    for parent in path.parents:
        if (parent / "pom.xml").exists() and parent != ROOT:
            return parent
    return None


def check_no_real_hosts(problems: list[str]) -> int:
    """Config test khong duoc tro toi IP that - guard nay khong phu thuoc IP cu the,
    nen doi IP ha tang (vi du 100.93.252.63 -> 172.31.42.27) khong lam no lac hau."""
    scanned = 0
    for group in MODULE_DIRS:
        for conf in sorted((ROOT / group).rglob("src/test/resources/application*.yml")):
            if "target" in conf.parts:
                continue
            scanned += 1
            for line_no, line in enumerate(conf.read_text(encoding="utf-8").splitlines(), 1):
                code = line.split("#", 1)[0]
                for ip in IPV4.findall(code):
                    if ip not in ALLOWED_IPS:
                        problems.append(
                            f"{conf.relative_to(ROOT)}:{line_no}: tro toi IP that {ip}"
                        )
    return scanned


def main() -> int:
    problems: list[str] = []
    checked = 0

    for group in MODULE_DIRS:
        for java in sorted((ROOT / group).rglob("src/test/java/**/*.java")):
            if "target" in java.parts:
                continue
            source = java.read_text(encoding="utf-8")
            if not SPRING_BOOT_TEST.search(source):
                continue
            checked += 1

            module = module_of(java)
            if module is None:
                problems.append(f"{java.relative_to(ROOT)}: khong xac dinh duoc module")
                continue

            test_resources = module / "src/test/resources"
            shadow = (test_resources / "application.yml").exists()
            profile_file = (test_resources / "application-test.yml").exists()
            annotated = bool(ACTIVE_PROFILES_TEST.search(source))

            if shadow:
                continue
            if annotated and profile_file:
                continue

            missing = []
            if not annotated:
                missing.append('@ActiveProfiles("test")')
            if not profile_file:
                missing.append("src/test/resources/application-test.yml")
            problems.append(
                f"{java.relative_to(ROOT)}: thieu {' + '.join(missing)} "
                f"(hoac them src/test/resources/application.yml de shadow config main)"
            )

    scanned = check_no_real_hosts(problems)
    print(f"Da kiem {checked} class @SpringBootTest va {scanned} file config test.")
    if problems:
        print("\nChua co lap - test se boot vao hang tang that:")
        for problem in problems:
            print(f"  - {problem}")
        return 1
    print("Tat ca class @SpringBootTest da duoc co lap.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
