#!/usr/bin/env python3
"""Convert a Windows TraceDiary plaintext export into an Android import ZIP.

The Android app imports either a plain DiaryBackup JSON payload or a ZIP with a
top-level ``tracediary_backup.json`` file. This script preserves the Windows
export manifest timestamps while packaging Markdown content into that JSON
shape.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Convert a Windows TraceDiary export directory to Android import ZIP."
    )
    parser.add_argument(
        "source",
        type=Path,
        help="Windows export directory containing manifest.json, diaries/, summaries/.",
    )
    parser.add_argument(
        "-o",
        "--output",
        type=Path,
        help="Output ZIP path. Defaults to output/import/<source>_android.zip.",
    )
    return parser.parse_args()


def load_manifest(source: Path) -> dict[str, Any]:
    manifest_path = source / "manifest.json"
    if not manifest_path.is_file():
        raise ValueError(f"missing manifest: {manifest_path}")
    with manifest_path.open("r", encoding="utf-8") as handle:
        manifest = json.load(handle)
    if not isinstance(manifest, dict):
        raise ValueError("manifest.json must be a JSON object")
    entries = manifest.get("entries")
    if not isinstance(entries, list):
        raise ValueError("manifest.entries must be a list")
    return manifest


def normalize_instant(value: Any, *, fallback: str) -> str:
    if not isinstance(value, str) or not value.strip():
        value = fallback
    try:
        if value.endswith("Z"):
            parsed = datetime.fromisoformat(value[:-1] + "+00:00")
        else:
            parsed = datetime.fromisoformat(value)
        return parsed.astimezone(timezone.utc).isoformat(timespec="milliseconds").replace(
            "+00:00", "Z"
        )
    except ValueError as exc:
        raise ValueError(f"invalid timestamp: {value}") from exc


def read_text_file(path: Path) -> str:
    if not path.is_file():
        raise ValueError(f"missing content file: {path}")
    return path.read_text(encoding="utf-8")


def convert_manifest(source: Path, manifest: dict[str, Any]) -> dict[str, Any]:
    generated_at = normalize_instant(
        manifest.get("generatedAt"),
        fallback=datetime.now(timezone.utc).isoformat(timespec="milliseconds"),
    )
    diary_entries: list[dict[str, str]] = []
    summaries: list[dict[str, Any]] = []
    seen_dates: set[str] = set()
    seen_years: set[int] = set()

    for raw_entry in manifest["entries"]:
        if not isinstance(raw_entry, dict):
            raise ValueError("manifest entries must be JSON objects")

        entry_type = raw_entry.get("type")
        relative_path = raw_entry.get("path")
        if not isinstance(relative_path, str) or not relative_path:
            raise ValueError(f"entry has invalid path: {raw_entry!r}")
        content = read_text_file(source / relative_path)
        created_at = normalize_instant(raw_entry.get("createdAt"), fallback=generated_at)
        updated_at = normalize_instant(raw_entry.get("modifiedAt"), fallback=created_at)

        if entry_type == "daily":
            date = raw_entry.get("date")
            if not isinstance(date, str) or not DATE_RE.match(date):
                raise ValueError(f"daily entry has invalid date: {raw_entry!r}")
            if date in seen_dates:
                raise ValueError(f"duplicate daily date: {date}")
            seen_dates.add(date)
            diary_entries.append(
                {
                    "date": date,
                    "content": content,
                    "createdAt": created_at,
                    "updatedAt": updated_at,
                }
            )
        elif entry_type == "yearly_summary":
            year = raw_entry.get("year")
            if not isinstance(year, int):
                raise ValueError(f"summary entry has invalid year: {raw_entry!r}")
            if year in seen_years:
                raise ValueError(f"duplicate yearly summary: {year}")
            seen_years.add(year)
            summaries.append(
                {
                    "year": year,
                    "content": content,
                    "createdAt": created_at,
                    "updatedAt": updated_at,
                }
            )
        else:
            raise ValueError(f"unsupported entry type: {entry_type!r}")

    expected_daily = manifest.get("dailyCount")
    expected_summaries = manifest.get("yearlySummaryCount")
    expected_total = manifest.get("entryCount")
    if expected_daily is not None and expected_daily != len(diary_entries):
        raise ValueError(f"daily count mismatch: manifest={expected_daily}, actual={len(diary_entries)}")
    if expected_summaries is not None and expected_summaries != len(summaries):
        raise ValueError(
            f"summary count mismatch: manifest={expected_summaries}, actual={len(summaries)}"
        )
    if expected_total is not None and expected_total != len(diary_entries) + len(summaries):
        raise ValueError(
            f"entry count mismatch: manifest={expected_total}, actual={len(diary_entries) + len(summaries)}"
        )

    diary_entries.sort(key=lambda item: item["date"])
    summaries.sort(key=lambda item: item["year"])
    return {
        "version": 1,
        "exportedAt": generated_at,
        "entries": diary_entries,
        "annualSummaries": summaries,
    }


def default_output_path(source: Path) -> Path:
    project_root = Path(__file__).resolve().parents[1]
    return project_root / "output" / "import" / f"{source.name}_android.zip"


def write_zip(output: Path, backup: dict[str, Any]) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    backup_json = json.dumps(backup, ensure_ascii=False, indent=2)
    manifest_json = json.dumps(
        {
            "version": 1,
            "exportedAt": backup["exportedAt"],
            "payloadKind": "json",
        },
        ensure_ascii=False,
        indent=2,
    )
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("tracediary_backup.json", backup_json)
        archive.writestr("manifest.json", manifest_json)


def main() -> int:
    args = parse_args()
    source = args.source.resolve()
    if not source.is_dir():
        print(f"source is not a directory: {source}", file=sys.stderr)
        return 2

    try:
        manifest = load_manifest(source)
        backup = convert_manifest(source, manifest)
        output = (args.output or default_output_path(source)).resolve()
        write_zip(output, backup)
    except Exception as exc:
        print(f"conversion failed: {exc}", file=sys.stderr)
        return 1

    print(f"wrote: {output}")
    print(f"entries: {len(backup['entries'])}")
    print(f"annualSummaries: {len(backup['annualSummaries'])}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
