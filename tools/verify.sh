#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 JoanComasFdz
# SPDX-License-Identifier: GPL-3.0-only
# Checks a patched APK for what a stub can't show (AGENTS.md, "Check a change", steps 5 and 6): every app or library
# class, method and field the extensions (com/joancomasfdz/**) refer to exists in the app with the same descriptor,
# and is public, or protected and inherited by the extension class that uses it.
#   tools/verify.sh <patched APK>
# Disassembles the APK with apktool into a temporary folder. A member is looked up in its class, then in the class's
# superclasses and interfaces. Classes Android provides (android/, java/, javax/, dalvik/, org/json/, org/w3c/,
# org/xml/, org/xmlpull/) and members found through one aren't checked. Reflection isn't checked either.
# Prints one line per problem, with the extension classes that have it. Exits 0 when there is none, 1 when there is
# one, 2 when the APK can't be disassembled.
set -euo pipefail

fail() { echo "verify: $*" >&2; exit 2; }

[ $# -eq 1 ] || fail "usage: tools/verify.sh <patched APK>"
apk=$1
[ -f "$apk" ] || fail "$apk is not a file"
command -v apktool >/dev/null || fail "no apktool on PATH"
command -v python3 >/dev/null || fail "no python3 on PATH"

out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
apktool d --no-res --force --output "$out/apk" "$apk" >"$out/apktool.log" 2>&1 ||
  { cat "$out/apktool.log" >&2; fail "apktool couldn't disassemble $apk"; }

python3 -I - "$out/apk" <<'EOF'
import glob, os, re, sys

EXTENSION = "Lcom/joancomasfdz/"
ANDROID = ("Landroid/", "Ljava/", "Ljavax/", "Ldalvik/", "Lorg/json/", "Lorg/w3c/", "Lorg/xml/", "Lorg/xmlpull/")

classes = {}
extension_files = []
for path in glob.glob(os.path.join(sys.argv[1], "smali*", "**", "*.smali"), recursive=True):
    with open(path, encoding="utf-8") as f:
        lines = f.read().splitlines()
    c = {"flags": set(), "super": None, "interfaces": [], "fields": {}, "methods": {}}
    name = None
    for line in lines:
        words = line.split()
        if not words:
            continue
        if words[0] == ".class":
            name, c["flags"] = words[-1], set(words[1:-1])
        elif words[0] == ".super":
            c["super"] = words[1]
        elif words[0] == ".implements":
            c["interfaces"].append(words[1])
        elif words[0] == ".field":
            member = words[-1] if "=" not in words else words[words.index("=") - 1]
            c["fields"][member] = set(words[1 : words.index(member)])
        elif words[0] == ".method":
            c["methods"][words[-1]] = set(words[1:-1])
    if name:
        classes[name] = c
        if name.startswith(EXTENSION):
            extension_files.append((name, lines))

MEMBER = re.compile(r"(L[^;\s]+;)->([^\s:(]+)(:\S+|\([^)]*\)\S+)")
OBJECT = "Ljava/lang/Object;"
# Every class ends at Object: its methods, so that a member missing from an app class isn't taken for one of Android's.
OBJECT_METHODS = {
    "<init>()V", "clone()Ljava/lang/Object;", "equals(Ljava/lang/Object;)Z", "finalize()V",
    "getClass()Ljava/lang/Class;", "hashCode()I", "notify()V", "notifyAll()V", "toString()Ljava/lang/String;",
    "wait()V", "wait(J)V", "wait(JI)V",
}
CLASS = re.compile(r"^\s*(?:new-instance|check-cast|const-class|instance-of|new-array|filled-new-array\S*)\b.*?\[*(L[^;\s]+;)\s*$")


def find(cls, kind, member):
    """The class that declares the member, None when it isn't there, "android" when Android's classes can't tell."""
    seen = set()
    todo = [cls]
    while todo:
        name = todo.pop(0)
        if name in seen:
            continue
        seen.add(name)
        c = classes.get(name)
        if name == OBJECT:
            if kind == "methods" and member in OBJECT_METHODS:
                return "android"
            continue
        if c is None:
            return "android"
        if member in c[kind]:
            return name
        todo += ([c["super"]] if c["super"] else []) + c["interfaces"]
    return None


def inherits(cls, ancestor):
    while cls in classes:
        if cls == ancestor:
            return True
        cls = classes[cls]["super"]
    return cls == ancestor


problems = {}


def report(user, problem):
    problems.setdefault(problem, set()).add(user)


def check_class(user, cls):
    if cls.startswith(EXTENSION):
        return
    c = classes.get(cls)
    if c is None:
        if not cls.startswith(ANDROID):
            report(user, f"{cls}: missing")
    elif "public" not in c["flags"]:
        report(user, f"{cls}: class not public")


for user, lines in extension_files:
    c = classes[user]
    for cls in ([c["super"]] if c["super"] else []) + c["interfaces"]:
        check_class(user, cls)
    for line in lines:
        m = CLASS.match(line)
        if m:
            check_class(user, m.group(1))
        for cls, name, descriptor in MEMBER.findall(line):
            if cls.startswith(EXTENSION):
                continue
            check_class(user, cls)
            if cls not in classes:
                continue
            kind = "fields" if descriptor.startswith(":") else "methods"
            member = name + descriptor
            owner = find(cls, kind, member)
            ref = f"{cls}->{member}"
            if owner is None:
                report(user, f"{ref}: missing")
            elif owner != "android":
                flags = classes[owner][kind][member]
                if "public" in flags:
                    continue
                if "protected" in flags:
                    if not inherits(user, owner):
                        report(user, f"{ref}: protected, and {user} doesn't extend {owner}")
                    continue
                access = "private" if "private" in flags else "package-private"
                report(user, f"{ref}: {access} in {owner}")

for problem in sorted(problems):
    print(f"{problem}  (used by {', '.join(sorted(problems[problem]))})")
print(f"verify: {len(extension_files)} extension classes, {len(problems)} problems")
sys.exit(1 if problems else 0)
EOF
