#!/usr/bin/env python3
"""
按 KOiL 的「二十进制」规则推进版本号。

规则（三段各自取值 0..20，满 20 进位）：
    1.1.19  → 1.1.20      末段 +1
    1.1.20  → 1.2.0       末段已到 20，再进一版就归零、中间段 +1
    1.2.20  → 1.3.0
    1.20.20 → 2.0.0       中间段也到 20，再往前进首段

没有任何自动递增逻辑参与构建：versionName 是手写在 app/build.gradle.kts 的常量，
versionCode 由 CI 注入（见该文件的注释）。本脚本只负责算出下一个 versionName 并改好
需要跟着一起改的地方，避免手算进位算错。

用法：
    python3 scripts/bump_version.py                # 只打印，不动文件
    python3 scripts/bump_version.py --apply        # 真改：versionName + 发布日期 + CHANGELOG 段标题
    python3 scripts/bump_version.py --apply --date 2026-12-01   # 指定发布日（默认今天）
    python3 scripts/bump_version.py --apply --no-changelog      # 只改构建脚本，不碰 CHANGELOG

--apply 之后仍要人工做两件事，脚本会打印出来：
    1. 在 CHANGELOG 新段下补条目（脚本只插入段标题，不替你写内容）
    2. 打 tag 时用大写 V：V<a.b.c>
"""

from __future__ import annotations

import argparse
import datetime as _dt
import re
import sys
from pathlib import Path

BASE = 20  # 每段最大取值，满即进位

ROOT = Path(__file__).resolve().parent.parent
GRADLE = ROOT / "app" / "build.gradle.kts"
CHANGELOG = ROOT / "CHANGELOG.md"

VERSION_RE = re.compile(r'(versionName\s*=\s*")(\d+)\.(\d+)\.(\d+)(")')
DATE_RE = re.compile(r'(KOIL_RELEASE_DATE", "\\")(\d{4}-\d{2}-\d{2})(\\")')


def parse_version(text: str) -> tuple[int, int, int]:
    m = VERSION_RE.search(text)
    if not m:
        sys.exit("error: 在 app/build.gradle.kts 里找不到 versionName")
    return int(m.group(2)), int(m.group(3)), int(m.group(4))


def next_version(v: tuple[int, int, int]) -> tuple[int, int, int]:
    a, b, c = v
    c += 1
    if c > BASE:
        c = 0
        b += 1
    if b > BASE:
        b = 0
        a += 1
    return a, b, c


def main() -> None:
    ap = argparse.ArgumentParser(description="按二十进制规则推进 KOiL 版本号")
    ap.add_argument("--apply", action="store_true", help="真的写文件（默认只打印）")
    ap.add_argument("--date", help="发布日期 YYYY-MM-DD，默认今天")
    ap.add_argument("--no-changelog", action="store_true", help="不插入 CHANGELOG 段标题")
    args = ap.parse_args()

    gradle_text = GRADLE.read_text(encoding="utf-8")
    cur = parse_version(gradle_text)
    nxt = next_version(cur)
    tag = "V{}.{}.{}".format(*nxt)
    date = args.date or _dt.date.today().isoformat()
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", date):
        sys.exit(f"error: --date 需要 YYYY-MM-DD，收到 {date}")

    print(f"当前：{cur[0]}.{cur[1]}.{cur[2]}")
    print(f"下一个：{nxt[0]}.{nxt[1]}.{nxt[2]}   （tag 用 {tag}）")
    print(f"发布日期：{date}")

    if not args.apply:
        print("\n这是预览。加 --apply 才会改文件。")
        return

    # 1) versionName
    new_name = "{}.{}.{}".format(*nxt)
    gradle_text = VERSION_RE.sub(lambda m: f'{m.group(1)}{new_name}{m.group(5)}', gradle_text, count=1)
    # 2) 发布日期
    m_date = DATE_RE.search(gradle_text)
    if not m_date:
        sys.exit("error: 找不到 KOIL_RELEASE_DATE")
    gradle_text = DATE_RE.sub(lambda m: f"{m.group(1)}{date}{m.group(3)}", gradle_text, count=1)
    GRADLE.write_text(gradle_text, encoding="utf-8")

    # 3) CHANGELOG：在最上面的版本段之前插入新段标题
    if not args.no_changelog:
        log = CHANGELOG.read_text(encoding="utf-8")
        head = "## {}\n".format(new_name)
        if head in log:
            print(f"notice: CHANGELOG 里已有 {head.strip()}，跳过插入")
        else:
            anchor = re.search(r"^## \d+\.\d+\.\d+", log, re.MULTILINE)
            if not anchor:
                sys.exit("error: CHANGELOG 里找不到任何版本段")
            log = log[: anchor.start()] + head + "\n" + log[anchor.start():]
            CHANGELOG.write_text(log, encoding="utf-8")

    print("\n已写入：")
    print(f"  app/build.gradle.kts  versionName = {new_name}")
    print(f"  app/build.gradle.kts  KOIL_RELEASE_DATE = {date}")
    if not args.no_changelog:
        print(f"  CHANGELOG.md          新增段标题「## {new_name}」")
    print("\n还要人工做：")
    print(f"  0. KOIL_RELEASE_DATE 要对齐 tag 所指提交的日期（通常就是合 main 那天），"
          f"允许当天或次日：跨天发版时用 --date <提交那天>"
          f"（CI 比对的是提交日期，不是打 tag 的日期）")
    print(f"  1. 在 CHANGELOG 的「## {new_name}」下补条目（脚本只插标题）")
    print(f"  2. 发版时打 tag：{tag}")


if __name__ == "__main__":
    main()
