#!/usr/bin/env python3
"""Version bumping script for FireClear releases.

Calculates and updates versionCode and versionName in app/build.gradle.kts
and prepends a release entry to CHANGELOG.md.
"""

from __future__ import annotations

import argparse
from datetime import date
import os
from pathlib import Path
import re
import sys


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Manage FireClear versions")
    parser.add_argument(
        "--bump",
        choices=["patch", "minor", "major"],
        default="patch",
        help="Type of semantic version bump",
    )
    parser.add_argument(
        "--calculate",
        action="store_true",
        help="Calculate versions and write to GITHUB_OUTPUT without modifying files",
    )
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Apply the version bump to app/build.gradle.kts and CHANGELOG.md",
    )
    return parser.parse_args()


def get_current_versions(gradle_path: Path) -> tuple[str, int, list[int]]:
    text = gradle_path.read_text(encoding="utf-8")
    vname_match = re.search(r'(?m)^\s*versionName\s*=\s*"([^"]+)"', text)
    vcode_match = re.search(r'(?m)^\s*versionCode\s*=\s*(\d+)', text)

    if not vname_match:
        raise ValueError(f"Could not find versionName in {gradle_path}")
    if not vcode_match:
        raise ValueError(f"Could not find versionCode in {gradle_path}")

    raw_vname = vname_match.group(1).strip()
    raw_parts = raw_vname.split(".")
    if not (1 <= len(raw_parts) <= 3):
        raise ValueError(f"Unsupported versionName format: {raw_vname}")

    parts = [int(p) for p in raw_parts]
    while len(parts) < 3:
        parts.append(0)

    current_code = int(vcode_match.group(1))
    return raw_vname, current_code, parts


def calculate_next(parts: list[int], current_code: int, bump_type: str) -> tuple[str, int, str]:
    major, minor, patch = parts
    if bump_type == "patch":
        patch += 1
    elif bump_type == "minor":
        minor += 1
        patch = 0
    elif bump_type == "major":
        major += 1
        minor = 0
        patch = 0
    else:
        raise ValueError(f"Unknown bump type: {bump_type}")

    new_version = f"{major}.{minor}.{patch}"
    new_code = current_code + 1
    new_tag = f"v{new_version}"
    return new_version, new_code, new_tag


def write_github_output(data: dict[str, str | int]) -> None:
    output_file = os.environ.get("GITHUB_OUTPUT")
    if not output_file:
        for k, v in data.items():
            print(f"{k}={v}")
        return

    with open(output_file, "a", encoding="utf-8") as fh:
        for k, v in data.items():
            fh.write(f"{k}={v}\n")


def apply_updates(
    gradle_path: Path,
    changelog_path: Path,
    current_version: str,
    new_version: str,
    current_code: int,
    new_code: int,
    bump_type: str,
) -> None:
    gradle_text = gradle_path.read_text(encoding="utf-8")

    gradle_text, count_code = re.subn(
        r'(?m)^(\s*versionCode\s*=\s*)\d+',
        rf"\g<1>{new_code}",
        gradle_text,
        count=1,
    )
    gradle_text, count_name = re.subn(
        r'(?m)^(\s*versionName\s*=\s*)"[^"]+"',
        rf'\g<1>"{new_version}"',
        gradle_text,
        count=1,
    )

    if count_code != 1 or count_name != 1:
        raise RuntimeError("Failed to update versionCode or versionName in gradle file")

    gradle_path.write_text(gradle_text, encoding="utf-8")

    changelog_text = changelog_path.read_text(encoding="utf-8")
    today = date.today().isoformat()
    entry = (
        f"## {new_version} - {today}\n"
        f"- Automated {bump_type} release bump from {current_version} (versionCode {current_code}) "
        f"to {new_version} (versionCode {new_code}).\n\n"
    )

    header_match = re.match(r"^#\s+Changelog\s*\n\n?", changelog_text)
    if header_match:
        pos = header_match.end()
        new_changelog = changelog_text[:pos] + entry + changelog_text[pos:]
    else:
        new_changelog = f"# Changelog\n\n{entry}{changelog_text}"

    changelog_path.write_text(new_changelog, encoding="utf-8")


def main() -> None:
    args = parse_args()
    root = Path(__file__).resolve().parent.parent
    gradle_path = root / "app" / "build.gradle.kts"
    changelog_path = root / "CHANGELOG.md"

    current_vname, current_code, parts = get_current_versions(gradle_path)
    new_version, new_code, new_tag = calculate_next(parts, current_code, args.bump)

    data = {
        "current_version": current_vname,
        "current_code": current_code,
        "new_version": new_version,
        "new_code": new_code,
        "new_tag": new_tag,
    }

    if args.calculate:
        write_github_output(data)
        return

    if args.apply:
        apply_updates(
            gradle_path,
            changelog_path,
            current_vname,
            new_version,
            current_code,
            new_code,
            args.bump,
        )
        write_github_output(data)
        print(f"Updated {gradle_path.name} and {changelog_path.name} to {new_version} ({new_code})")
        return

    # Default: both calculate and output
    write_github_output(data)


if __name__ == "__main__":
    main()

