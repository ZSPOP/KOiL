#!/usr/bin/env python3
"""KOiL v1.4.159 命名统一：资源 key 加 koil_ 前缀 + Fuel 标识符改 KOiL。"""
import os, re, sys

ROOT = "/home/user/Doubao/chats/38449036817661954"
RES = os.path.join(ROOT, "app/src/main/res")

# ---------- 1. 资源 key：统一加 koil_ 前缀 ----------
key_map = {}
key_re = re.compile(r'name="([a-z_0-9]+)"')
for lang in ["values", "values-zh-rTW", "values-en"]:
    p = os.path.join(RES, lang, "strings.xml")
    with open(p, encoding="utf-8") as f:
        content = f.read()
    for m in key_re.finditer(content):
        old = m.group(1)
        if old not in key_map:
            key_map[old] = "koil_" + old
    new_content = key_re.sub(lambda m: 'name="koil_%s"' % m.group(1), content)
    with open(p, "w", encoding="utf-8") as f:
        f.write(new_content)
print("资源 key 映射:", len(key_map), "个")

# ---------- 2. 代码里 R.string.xxx 同步 ----------
def replace_in(path, pairs):
    with open(path, encoding="utf-8") as f:
        c = f.read()
    for old, new in pairs:
        c = c.replace(old, new)
    with open(path, "w", encoding="utf-8") as f:
        f.write(c)

kt_files = []
for dirpath, _, files in os.walk(os.path.join(ROOT, "app/src/main")):
    for fn in files:
        if fn.endswith(".kt"):
            kt_files.append(os.path.join(dirpath, fn))

# R.string 引用
str_pairs = [(f"R.string.{old}", f"R.string.{new}") for old, new in key_map.items()]
for fp in kt_files:
    replace_in(fp, str_pairs)
print("代码 R.string 引用已同步，文件数:", len(kt_files))

# ---------- 3. Fuel 标识符 → KOiL ----------
id_pairs = [
    ("FuelPriceDao", "KOiLPriceDao"),
    ("FuelPriceEntity", "KOiLPriceEntity"),
    ("fuelPriceDao", "koilPriceDao"),
    ("fuelPriceEntity", "koilPriceEntity"),
    ("fuel_prices", "koil_prices"),
    ("FuelPriceEntity.kt", "KOiLPriceEntity.kt"),   # 注释/导入里的文件名
    ("FuelPriceDao.kt", "KOiLPriceDao.kt"),
]
for fp in kt_files:
    replace_in(fp, id_pairs)

# 物理重命名
old_dao = os.path.join(ROOT, "app/src/main/java/com/llpop/koil/data/local/dao/FuelPriceDao.kt")
new_dao = os.path.join(ROOT, "app/src/main/java/com/llpop/koil/data/local/dao/KOiLPriceDao.kt")
old_ent = os.path.join(ROOT, "app/src/main/java/com/llpop/koil/data/local/entity/FuelPriceEntity.kt")
new_ent = os.path.join(ROOT, "app/src/main/java/com/llpop/koil/data/local/entity/KOiLPriceEntity.kt")
os.rename(old_dao, new_dao)
os.rename(old_ent, new_ent)
print("文件重命名完成:", os.path.basename(new_dao), os.path.basename(new_ent))

# ---------- 4. 校验 ----------
leftover = []
for fp in kt_files:
    with open(fp, encoding="utf-8") as f:
        c = f.read()
    if "FuelPrice" in c or "fuelPriceDao" in c or "fuelPriceEntity" in c:
        leftover.append(fp)
print("残留 Fuel 标识符文件:", leftover or "无")
print("完成")
