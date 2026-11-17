#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""三语 strings.xml 一致性检查：KOiL 的 values / values-en / values-zh-rTW。

为什么需要它：
    Android 的运行时回落是**静默**的 —— values-en 少一条 key，英文界面不会崩、不会
    报警，只会悄悄显示简中那一句。这类缺陷只有在英文界面刷到那一屏才看得见，所以在
    仓库里必须有一道机器检查兜住它，而不是靠人在改动时数一遍 400 多条。

检查什么（四类，全部是「不检查就会静默劣化」的）：
    1. key 集合不一致 —— 某语言缺 / 多某条 string 或 plurals；
    2. 占位符不一致 —— 同一 key 在三种语言里的 `%1$s` / `%2$d` 数量、序号、类型不同。
       这比缺 key 更危险：stringResource 少传一个参数会在运行时抛
       IllegalFormatException，而不是显示错误文案；
    3. plurals 内部不一致 —— 同一条 plurals 的 one / other 等 quantity 之间占位符不同；
    4. plurals 缺 other —— other 是 Android 的兜底 quantity，缺了在部分 locale 下取不到值。

不做的事（避免误报）：
    - 不要求各语言的 quantity 集合相同。中文只有 other 是正确的，英文必须 one + other；
    - 不比对文案本身。措辞该不该一致是人的判断，机器只看结构与占位符。

用法：
    python3 scripts/check_i18n_parity.py            # 检查仓库里的三份文件
    python3 scripts/check_i18n_parity.py --self-test  # 用内存里的假数据演练「坏了会不会报」
"""

from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
RES_DIR = REPO_ROOT / "app" / "src" / "main" / "res"

# 基准语言是 values（简中）；其余两门必须与它结构一致。
BASE_LOCALE = "values"
LOCALES = ("values", "values-en", "values-zh-rTW")
LOCALE_LABELS = {"values": "简中", "values-en": "英文", "values-zh-rTW": "繁中"}

# %1$s / %2$d / %s / %d / %1$.2f / %% —— 只关心「占位符序号 + 类型」，不关心宽度与精度。
_PLACEHOLDER = re.compile(r"%%|%(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z])")


def placeholder_signature(text: str) -> tuple[tuple[int, str, int], ...]:
    """把文案里的占位符抽成可比较的签名。

    返回 ((序号, 类型字符, 出现次数), ...)：
    - 显式序号（`%1$s`）用它自己的数字；
    - 隐式序号（`%s` / `%d`）按出现顺序从 1 开始编号 —— Java 的 Formatter 就是这么消费
      参数的，所以 `"%s ... %d"` 与 `"%1$s ... %2$d"` 等价，应当判为一致；
    - `%%` 是转义的百分号，不消费参数，必须排除，否则 `"100%%"` 会被误判成有一个占位符。
    """
    entries: list[tuple[int, str, int]] = []
    implicit = 0
    for match in _PLACEHOLDER.finditer(text):
        raw = match.group(0)
        if raw == "%%":
            continue
        index = match.group(1)
        kind = match.group(2)
        if index is None:
            implicit += 1
            position = implicit
        else:
            position = int(index)
        entries.append((position, kind, 1))

    merged: dict[tuple[int, str], int] = {}
    for position, kind, count in entries:
        key = (position, kind)
        merged[key] = merged.get(key, 0) + count
    return tuple(sorted((k[0], k[1], v) for k, v in merged.items()))


def describe_signature(signature: tuple[tuple[int, str, int], ...]) -> str:
    if not signature:
        return "无占位符"
    parts = []
    for position, kind, count in signature:
        suffix = f"×{count}" if count > 1 else ""
        parts.append(f"%{position}${kind}{suffix}")
    return " ".join(parts)


def ensure_other_quantity(name: str, items: dict[str, str], path: Path) -> None:
    """plurals 必须有 other —— 它是 Android 在所有 locale 下的兜底 quantity。

    抽成独立函数是为了能被 --self-test 直接演练：这条检查发生在解析阶段，
    如果只藏在 _parse 里，演练就只能构造「解析完成后」的坏数据，永远测不到它。
    """
    if "other" not in items:
        raise SystemExit(f"[FAIL] {path.name}: 条目 `{name}` plurals 缺 other（Android 的兜底 quantity）")


class LocaleFile:
    """一份 strings.xml 的结构化内容：string 与 plurals 分开收。"""

    def __init__(self, locale: str) -> None:
        self.locale = locale
        self.path = RES_DIR / locale / "strings.xml"
        self.strings: dict[str, str] = {}
        self.plurals: dict[str, dict[str, str]] = {}
        self.line_numbers: dict[str, int] = {}
        self._parse()

    def _parse(self) -> None:
        if not self.path.is_file():
            raise SystemExit(f"[FAIL] 找不到资源文件：{self.path}")

        raw_lines = self.path.read_text(encoding="utf-8").splitlines()
        for number, line in enumerate(raw_lines, start=1):
            match = re.search(r'<(?:string|plurals)\s+name="([^"]+)"', line)
            if match:
                self.line_numbers.setdefault(match.group(1), number)

        root = ET.parse(self.path).getroot()
        for element in root:
            name = element.get("name")
            if element.tag == "string":
                if name in self.plurals:
                    self._conflict(name, "同时出现在 string 与 plurals")
                self.strings[name] = element.text or ""
            elif element.tag == "plurals":
                if name in self.strings:
                    self._conflict(name, "同时出现在 string 与 plurals")
                items = {}
                for item in element:
                    items[item.get("quantity")] = item.text or ""
                ensure_other_quantity(name, items, self.path)
                self.plurals[name] = items

    def _conflict(self, name: str, reason: str) -> None:
        raise SystemExit(f"[FAIL] {self.path.name}: 条目 `{name}` {reason}")

    def location(self, name: str) -> str:
        line = self.line_numbers.get(name)
        return f"{self.path.relative_to(REPO_ROOT)}:{line}" if line else str(self.path.relative_to(REPO_ROOT))


def check(base: LocaleFile, other: LocaleFile, problems: list[str]) -> None:
    label = LOCALE_LABELS[other.locale]

    missing_strings = sorted(set(base.strings) - set(other.strings))
    extra_strings = sorted(set(other.strings) - set(base.strings))
    missing_plurals = sorted(set(base.plurals) - set(other.plurals))
    extra_plurals = sorted(set(other.plurals) - set(base.plurals))

    for name in missing_strings:
        problems.append(f"[缺 key] {label} 少 string `{name}`（基准见 {base.location(name)}）")
    for name in extra_strings:
        problems.append(f"[多 key] {label} 多出 string `{name}`（{other.location(name)}）")
    for name in missing_plurals:
        problems.append(f"[缺 key] {label} 少 plurals `{name}`（基准见 {base.location(name)}）")
    for name in extra_plurals:
        problems.append(f"[多 key] {label} 多出 plurals `{name}`（{other.location(name)}）")

    # 占位符：同一 key 三语必须一致。缺 key 的已经报过，这里只比双方都有的。
    for name in sorted(set(base.strings) & set(other.strings)):
        expected = placeholder_signature(base.strings[name])
        actual = placeholder_signature(other.strings[name])
        if expected != actual:
            problems.append(
                f"[占位符] string `{name}`：简中 {describe_signature(expected)} / "
                f"{label} {describe_signature(actual)}（{other.location(name)}）"
            )

    for name in sorted(set(base.plurals) & set(other.plurals)):
        # 基准内部先自检：同一条 plurals 的各个 quantity 占位符必须一致，
        # 否则切到 one / other 时同一句文案的参数会不一样。
        base_signatures = {q: placeholder_signature(t) for q, t in base.plurals[name].items()}
        if len(set(base_signatures.values())) > 1:
            problems.append(
                f"[plurals 内部] `{name}` 简中各 quantity 占位符不一致："
                + " / ".join(f"{q} {describe_signature(s)}" for q, s in sorted(base_signatures.items()))
            )

        expected = base_signatures.get("other")
        for quantity, text in sorted(other.plurals[name].items()):
            actual = placeholder_signature(text)
            if expected is not None and actual != expected:
                problems.append(
                    f"[plurals 占位符] `{name}` 的 quantity `{quantity}`：简中 other "
                    f"{describe_signature(expected)} / {label} {describe_signature(actual)}"
                    f"（{other.location(name)}）"
                )


def run_checks(files: dict[str, LocaleFile]) -> list[str]:
    problems: list[str] = []
    base = files[BASE_LOCALE]
    for locale in LOCALES:
        if locale == BASE_LOCALE:
            continue
        check(base, files[locale], problems)
    return problems


def load_all() -> dict[str, LocaleFile]:
    return {locale: LocaleFile(locale) for locale in LOCALES}


def self_test() -> int:
    """演练检查真的会红：在内存里造四种已知坏法，逐条确认能报出来。

    这四种坏法就是本脚本存在的理由 —— 缺 key、多 key、占位符 arity 不同、
    plurals 缺 other。任何一条「改了脚本还全绿」都说明检查退化了。
    """
    originals = load_all()

    def clone() -> dict[str, LocaleFile]:
        # 直接复用已解析的对象，坏法通过改副本里的字典实现，不写盘、不碰真实文件。
        return {locale: LocaleFile(locale) for locale in LOCALES}

    cases: list[tuple[str, str, callable]] = []

    def case_missing_key(files):
        del files["values-en"].strings["koil_pdb_countdown"]

    def case_extra_key(files):
        files["values-en"].strings["__self_test_extra__"] = "x"

    def case_bad_arity(files):
        files["values-zh-rTW"].strings["koil_pdb_countdown"] = "%1$s 起調價"

    def case_missing_other(files):
        # 这条检查在解析阶段触发，演练直接调那个函数，构造「只有 one、没有 other」的坏数据。
        ensure_other_quantity("__self_test_plurals__", {"one": "%1$d 条"}, files["values"].path)

    cases = [
        ("缺 key", "[缺 key]", case_missing_key),
        ("多 key", "[多 key]", case_extra_key),
        ("占位符不一致", "[占位符]", case_bad_arity),
        ("plurals 缺 other", "[FAIL]", case_missing_other),
    ]

    failures = 0
    for label, expected_marker, mutate in cases:
        files = clone()
        try:
            mutate(files)
            problems = run_checks(files)
        except SystemExit as exc:  # plurals 缺 other 在解析阶段就会抛
            message = str(exc)
            ok = expected_marker in message
            print(f"{'PASS' if ok else 'FAIL'} 演练「{label}」：{'按预期报错' if ok else '没报错'} —— {message}")
            failures += 0 if ok else 1
            continue
        ok = any(expected_marker in problem for problem in problems)
        print(f"{'PASS' if ok else 'FAIL'} 演练「{label}」：{'按预期报错' if ok else '没报错'} —— {problems or '无输出'}")
        failures += 0 if ok else 1

    # 真实文件在演练后必须仍然全绿，否则说明演练污染了状态或真实文件本来就有问题。
    real_problems = run_checks(originals)
    ok = not real_problems
    print(f"{'PASS' if ok else 'FAIL'} 演练后真实文件仍为绿：{real_problems or '无问题'}")
    failures += 0 if ok else 1

    return 1 if failures else 0


def main() -> int:
    parser = argparse.ArgumentParser(description="三语 strings.xml 结构与占位符一致性检查")
    parser.add_argument("--self-test", action="store_true", help="演练四种坏法能否被检查拦下")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    files = load_all()
    counts = {locale: (len(f.strings), len(f.plurals)) for locale, f in files.items()}
    for locale in LOCALES:
        strings, plurals = counts[locale]
        print(f"{LOCALE_LABELS[locale]:<3}({locale:<14}) {strings} strings + {plurals} plurals = {strings + plurals}")

    problems = run_checks(files)
    if problems:
        print()
        for problem in problems:
            print(problem)
        print(f"\n[FAIL] 共 {len(problems)} 处不一致。")
        return 1

    print("\n[OK] 三语 key、占位符与 plurals 结构一致。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
