#!/usr/bin/env python3
"""Generates fx_defs_data.h from the Kotlin ClipFx definition table.

History: the 167-entry effect definition table of ClipFxLibrary (model/ClipFx.kt)
was migrated from Kotlin to C++ (fx_defs.cpp) so the C++ table is the single
source of truth. This script performed the migration: it parses the Kotlin
table (the FxDef list, the extraValues defaults and the extNativeType /
ext2 / ext3 / ext4 group maps) and emits the C++ data header. It is kept so
the provenance of fx_defs_data.h is reproducible; the generated header was
verified byte-identical (field by field, floats as raw bits) against a dump
of the original Kotlin table before the Kotlin table was deleted.

Usage: python3 fx_defs_gen.py <path-to-ClipFx.kt> <output-header>
"""
import math
import re
import struct
import sys


def f32(x):
    return struct.unpack("f", struct.pack("f", x))[0]


def strip_comments(src):
    out = []
    i, n = 0, len(src)
    in_str = False
    while i < n:
        c = src[i]
        if in_str:
            out.append(c)
            if c == "\\":
                if i + 1 < n:
                    out.append(src[i + 1])
                    i += 2
                    continue
            elif c == '"':
                in_str = False
            i += 1
        elif c == '"':
            in_str = True
            out.append(c)
            i += 1
        elif c == "/" and i + 1 < n and src[i + 1] == "/":
            while i < n and src[i] != "\n":
                i += 1
        elif c == "/" and i + 1 < n and src[i + 1] == "*":
            i += 2
            while i + 1 < n and not (src[i] == "*" and src[i + 1] == "/"):
                i += 1
            i += 2
        else:
            out.append(c)
            i += 1
    return "".join(out)


def balanced(src, start):
    """src[start] is '(' or '{'; return (inner, index just past the closer)."""
    open_c = src[start]
    close_c = ")" if open_c == "(" else "}"
    depth = 0
    in_str = False
    i = start
    while i < len(src):
        c = src[i]
        if in_str:
            if c == "\\":
                i += 2
                continue
            if c == '"':
                in_str = False
        elif c == '"':
            in_str = True
        elif c == open_c:
            depth += 1
        elif c == close_c:
            depth -= 1
            if depth == 0:
                return src[start + 1:i], i + 1
        i += 1
    raise ValueError("unbalanced from %d" % start)


def split_top(s):
    parts = []
    depth = 0
    in_str = False
    cur = []
    i = 0
    while i < len(s):
        c = s[i]
        if in_str:
            cur.append(c)
            if c == "\\":
                if i + 1 < len(s):
                    cur.append(s[i + 1])
                    i += 2
                    continue
            elif c == '"':
                in_str = False
        elif c == '"':
            in_str = True
            cur.append(c)
        elif c in "([{":
            depth += 1
            cur.append(c)
        elif c in ")]}":
            depth -= 1
            cur.append(c)
        elif c == "," and depth == 0:
            parts.append("".join(cur).strip())
            cur = []
        else:
            cur.append(c)
        i += 1
    tail = "".join(cur).strip()
    if tail:
        parts.append(tail)
    return parts


def parse_kotlin_string(tok):
    tok = tok.strip()
    assert tok.startswith('"') and tok.endswith('"'), tok
    body = tok[1:-1]
    out = []
    i = 0
    while i < len(body):
        c = body[i]
        if c == "\\":
            n = body[i + 1]
            if n == "n":
                out.append("\n"); i += 2
            elif n == "t":
                out.append("\t"); i += 2
            elif n == "r":
                out.append("\r"); i += 2
            elif n == '"':
                out.append('"'); i += 2
            elif n == "\\":
                out.append("\\"); i += 2
            elif n == "u":
                out.append(chr(int(body[i + 2:i + 6], 16))); i += 6
            else:
                raise ValueError("escape \\%s" % n)
        else:
            out.append(c)
            i += 1
    return "".join(out)


def parse_float(tok):
    tok = tok.strip()
    m = re.fullmatch(r"0x([0-9A-Fa-f]+)\.toFloat\(\)", tok)
    if m:
        return f32(float(int(m.group(1), 16)))
    if tok.endswith("f"):
        tok = tok[:-1]
    return f32(float(tok))


def kotlin_round_to_int(x):
    # kotlin.math.roundToInt on the JVM is Math.round: floor(x + 0.5f) in float.
    return math.floor(f32(f32(x) + 0.5))


def pack_point(x, y):
    ix = min(2047, max(-2048, kotlin_round_to_int(x))) + 2048
    iy = min(2047, max(-2048, kotlin_round_to_int(y))) + 2048
    return f32(ix * 4096 + iy)


def parse_extra_value(tok):
    tok = tok.strip()
    m = re.fullmatch(r"packPoint\(([^,]+),([^)]+)\)", tok)
    if m:
        return pack_point(parse_float(m.group(1)), parse_float(m.group(2)))
    return parse_float(tok)


def parse_when_branches(body):
    """Parse `KEY, KEY -> value` branches of a when block. Returns {key: raw_value_str}."""
    branches = {}
    i, n = 0, len(body)
    while i < n:
        # skip whitespace and stray commas/braces
        while i < n and body[i] in " \t\r\n":
            i += 1
        if i >= n:
            break
        arrow = body.index("->", i)
        keys = [k.strip() for k in body[i:arrow].split(",") if k.strip()]
        j = arrow + 2
        while j < n and body[j] in " \t":
            j += 1
        if body.startswith("listOf", j):
            inner, j = balanced(body, body.index("(", j))
            raw = ("listOf", inner)
        elif body.startswith("emptyList", j):
            j = body.index(")", j) + 1
            raw = ("empty", None)
        elif body.startswith("List", j):
            m = re.match(r"List\((\d+)\)\s*\{\s*([^}]*)\}", body[j:])
            raw = ("repeat", (int(m.group(1)), m.group(2)))
            j += m.end()
        elif body.startswith("null", j):
            raw = ("null", None)
            j += 4
        else:
            m = re.match(r"-?\d+", body[j:])
            raw = ("int", int(m.group(0)))
            j += m.end()
        for k in keys:
            branches[k] = raw
        i = j
    return branches


def fmt_float(x):
    s = "%.9g" % x
    if "e" in s or "E" in s:
        mant, ex = re.split("[eE]", s)
        if "." not in mant:
            mant += ".0"
        return mant + "e" + ex + "f"
    if "." not in s:
        s += ".0"
    return s + "f"


def c_string(s):
    b = s.encode("utf-8")
    out = []
    for byte in b:
        if byte == 0x22:
            out.append('\\"')
        elif byte == 0x5C:
            out.append("\\\\")
        elif 0x20 <= byte <= 0x7E:
            out.append(chr(byte))
        else:
            out.append("\\%03o" % byte)
    return '"' + "".join(out) + '"'


def main():
    src_path, out_path = sys.argv[1], sys.argv[2]
    src = strip_comments(open(src_path, encoding="utf-8").read())

    consts = {}
    for m in re.finditer(r"const val ([A-Z0-9_]+) = (\"(?:[^\"\\]|\\.)*\")", src):
        consts[m.group(1)] = parse_kotlin_string(m.group(2))

    # ---- the FxDef table ----
    all_pos = src.index("val all: List<FxDef> = listOf(")
    inner, _ = balanced(src, src.index("(", all_pos))
    defs = []
    for entry in split_top(inner):
        assert entry.startswith("FxDef("), entry[:60]
        einner, _ = balanced(entry, entry.index("("))
        args = split_top(einner)
        assert len(args) == 6, (args[0], len(args))
        fx_id = consts[args[0].strip()]
        native_type = int(args[1])
        name = parse_kotlin_string(args[2])
        cat = parse_kotlin_string(args[3])
        blurb = parse_kotlin_string(args[4])
        params = []
        plist = args[5].strip()
        assert plist.startswith("listOf("), plist[:40]
        pinner, _ = balanced(plist, plist.index("("))
        if pinner.strip():
            for p in split_top(pinner):
                assert p.startswith("FxParamDef("), p[:60]
                pinner2, _ = balanced(p, p.index("("))
                pargs = split_top(pinner2)
                pos = []
                named = {}
                for a in pargs:
                    m = re.match(r"^([a-zA-Z]+)\s*=\s*(.+)$", a, re.S)
                    if m and not a.lstrip().startswith('"'):
                        named[m.group(1)] = m.group(2).strip()
                    else:
                        pos.append(a)
                pname = parse_kotlin_string(pos[0])
                pmin = parse_extra_value(pos[1])
                pmax = parse_extra_value(pos[2])
                pdef = parse_extra_value(pos[3])
                unit = parse_kotlin_string(pos[4]) if len(pos) > 4 else ""
                if "unit" in named:
                    unit = parse_kotlin_string(named["unit"])
                step = parse_float(named["step"]) if "step" in named else 1.0
                if len(pos) > 5:
                    step = parse_float(pos[5])
                params.append((pname, pmin, pmax, pdef, unit, step))
        defs.append({
            "id": fx_id, "type": native_type, "name": name, "cat": cat,
            "blurb": blurb, "params": params, "extras": [], "ext": [-1, -1, -1, -1],
        })

    by_id = {d["id"]: d for d in defs}

    # ---- extraValues ----
    pos = src.index("fun extraValues")
    wpos = src.index("when (type) {", pos)
    wbody, _ = balanced(src, src.index("{", wpos))
    for key, raw in parse_when_branches(wbody).items():
        if key == "else":
            continue
        vals = []
        if raw[0] == "listOf":
            if raw[1].strip():
                vals = [parse_extra_value(t) for t in split_top(raw[1])]
        elif raw[0] == "repeat":
            vals = [parse_float(raw[1][1])] * raw[1][0]
        by_id[consts[key]]["extras"] = vals

    # ---- ext group maps ----
    for fname, slot in (("extNativeType", 0), ("ext2NativeType", 1),
                        ("ext3NativeType", 2), ("ext4NativeType", 3)):
        pos = src.index("fun " + fname)
        wpos = src.index("when (type) {", pos)
        wbody, _ = balanced(src, src.index("{", wpos))
        for key, raw in parse_when_branches(wbody).items():
            if key == "else":
                continue
            assert raw[0] == "int", (fname, key, raw)
            by_id[consts[key]]["ext"][slot] = raw[1]

    # ---- validation ----
    assert len(defs) == 167, len(defs)
    assert len({d["id"] for d in defs}) == 167
    assert len({d["type"] for d in defs}) == 167
    total_params = sum(len(d["params"]) for d in defs)

    # ---- emit ----
    lines = []
    lines.append("// GENERATED by fx_defs_gen.py from the Kotlin ClipFx table (model/ClipFx.kt)")
    lines.append("// at the Kotlin -> C++ migration. fx_defs.cpp is now the single source of")
    lines.append("// truth for the effect definitions; regenerate only to re-derive from a")
    lines.append("// Kotlin table, never hand-edit this header.")
    lines.append("#pragma once")
    lines.append("")
    lines.append("namespace am {")
    lines.append("")
    for i, d in enumerate(defs):
        if d["params"]:
            rows = []
            for (pn, pmin, pmax, pdef, unit, step) in d["params"]:
                rows.append("    {%s, %s, %s, %s, %s, %s}" % (
                    c_string(pn), fmt_float(pmin), fmt_float(pmax),
                    fmt_float(pdef), fmt_float(step), c_string(unit)))
            lines.append("static const FxParamDefData kFxParams%d[] = {" % i)
            lines.append(",\n".join(rows))
            lines.append("};")
        if d["extras"]:
            lines.append("static const float kFxExtras%d[] = {%s};" % (
                i, ", ".join(fmt_float(v) for v in d["extras"])))
        lines.append("")
    lines.append("static const FxDefData kFxDefs[] = {")
    rows = []
    for i, d in enumerate(defs):
        params_ref = "kFxParams%d, %d" % (i, len(d["params"])) if d["params"] else "nullptr, 0"
        extras_ref = "kFxExtras%d, %d" % (i, len(d["extras"])) if d["extras"] else "nullptr, 0"
        rows.append("    {%s, %d, %s, %s, %s, %s, %s, %d, %d, %d, %d}" % (
            c_string(d["id"]), d["type"], c_string(d["name"]), c_string(d["cat"]),
            c_string(d["blurb"]), params_ref, extras_ref,
            d["ext"][0], d["ext"][1], d["ext"][2], d["ext"][3]))
    lines.append(",\n".join(rows))
    lines.append("};")
    lines.append("")
    lines.append("static const int kFxDefCount = %d;" % len(defs))
    lines.append("")
    lines.append("}  // namespace am")
    lines.append("")
    open(out_path, "w", encoding="utf-8").write("\n".join(lines))
    print("defs=%d params=%d extras=%d" % (
        len(defs), total_params, sum(len(d["extras"]) for d in defs)))


if __name__ == "__main__":
    main()
