#!/usr/bin/env python3
"""Compare two APKs for parity: zip entries, dex class sets, resource table, signing.

Standard library only. Exit code 0 when every difference is covered by the allow-list,
1 when at least one difference is not, 2 on usage errors.

Compared (differences, subject to the allow-list):
  zip.added / zip.removed   entry names present in only one APK (all entries)
  zip.changed               content hash differs (all entries except classes*.dex,
                            resources.arsc and signature files under META-INF)
  dex.added / dex.removed   class descriptors present in only one APK (all classes*.dex)
  arsc.added / arsc.removed resource names (type/name) present in only one table
  arsc.id.changed           same resource name with a different resource id
  arsc.value.changed        same resource name, config set or value text differs
  signing.verify            apksigner verification result differs
  signing.cert              signer certificate digests differ

Informational only (counted in the report, never a failure):
  zip.compression           entry stored in one APK, deflated in the other
  zip.alignment             data offset alignment differs (4 bytes, or 16 KB for lib/*.so)
  dex.moved                 class present in both APKs but in a different classes*.dex
  signing.files             META-INF signature files differ (their bytes are ignored)

Allow-list format (--allow FILE): one rule per line, "<category-glob> <subject-glob>",
split on the first whitespace. Blank lines and lines starting with "#" are ignored.
Rules are matched with fnmatch against the category and subject of each difference.
Example:
  zip.added classes*.dex
  arsc.value.changed *
  zip.changed res/values/*
"""

import argparse
import fnmatch
import hashlib
import json
import os
import re
import struct
import subprocess
import sys
import zipfile
from collections import Counter, defaultdict

DEFAULT_BUILD_TOOLS = os.path.join(
    os.environ.get("ANDROID_HOME")
    or os.path.join(os.environ.get("LOCALAPPDATA", os.path.expanduser("~")), "Android", "Sdk"),
    "build-tools",
    "36.0.0",
)

DEX_NAME = re.compile(r"^classes\d*\.dex$")
SIGNATURE_FILE = re.compile(r"^META-INF/(MANIFEST\.MF|[^/]+\.(SF|RSA|DSA|EC))$", re.IGNORECASE)
RESOURCE_LINE = re.compile(r"^(\s*)resource (0x[0-9a-fA-F]{8}) (\S+)")
CONFIG_LINE = re.compile(r"^config\s*:?\s*(.*)$")


class Difference:
    def __init__(self, category, subject, detail=""):
        self.category = category
        self.subject = subject
        self.detail = detail

    def as_dict(self):
        return {"category": self.category, "subject": self.subject, "detail": self.detail}


# ---------------------------------------------------------------- zip

def _data_offset(fh, info):
    fh.seek(info.header_offset)
    header = fh.read(30)
    if header[:4] != b"PK\x03\x04":
        raise ValueError(f"bad local header for {info.filename}")
    name_len, extra_len = struct.unpack_from("<HH", header, 26)
    return info.header_offset + 30 + name_len + extra_len


def _digest(zf, name):
    h = hashlib.sha256()
    with zf.open(name) as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def read_zip(path):
    """Returns {name: {"sha256": str|None, "stored": bool, "align4": bool, "align16k": bool}}.

    The sha256 is None for dex files, resources.arsc and signature files; those are compared
    by their own parsers or ignored.
    """
    entries = {}
    with zipfile.ZipFile(path) as zf, open(path, "rb") as fh:
        for info in zf.infolist():
            name = info.filename
            offset = _data_offset(fh, info)
            skip_hash = DEX_NAME.match(name) or name == "resources.arsc" or SIGNATURE_FILE.match(name)
            entries[name] = {
                "sha256": None if skip_hash else _digest(zf, name),
                "stored": info.compress_type == zipfile.ZIP_STORED,
                "align4": offset % 4 == 0,
                "align16k": offset % 16384 == 0,
                "is_lib_so": name.startswith("lib/") and name.endswith(".so"),
            }
    return entries


def read_signature_files(path):
    with zipfile.ZipFile(path) as zf:
        return {
            info.filename: zf.read(info.filename)
            for info in zf.infolist()
            if SIGNATURE_FILE.match(info.filename)
        }


# ---------------------------------------------------------------- dex

def dex_class_descriptors(data):
    """Class descriptors (e.g. Lcom/example/Foo;) of one dex file, read from its tables."""
    if data[:4] != b"dex\n":
        raise ValueError("not a dex file")
    string_ids_size, string_ids_off = struct.unpack_from("<II", data, 56)
    type_ids_size, type_ids_off = struct.unpack_from("<II", data, 64)
    class_defs_size, class_defs_off = struct.unpack_from("<II", data, 96)

    def string_at(index):
        if index >= string_ids_size:
            raise ValueError("string index out of range")
        pos = struct.unpack_from("<I", data, string_ids_off + 4 * index)[0]
        while data[pos] & 0x80:  # skip the ULEB128 UTF-16 length
            pos += 1
        pos += 1
        end = data.index(0, pos)
        return data[pos:end].decode("utf-8", "surrogatepass")

    def type_at(index):
        if index >= type_ids_size:
            raise ValueError("type index out of range")
        return string_at(struct.unpack_from("<I", data, type_ids_off + 4 * index)[0])

    return [
        type_at(struct.unpack_from("<I", data, class_defs_off + 32 * i)[0])
        for i in range(class_defs_size)
    ]


def read_dex_classes(path):
    """Returns {descriptor: dex entry name} across all classes*.dex of the APK."""
    classes = {}
    with zipfile.ZipFile(path) as zf:
        for name in sorted(n for n in zf.namelist() if DEX_NAME.match(n)):
            for descriptor in dex_class_descriptors(zf.read(name)):
                classes.setdefault(descriptor, name)
    return classes


# ---------------------------------------------------------------- resources

def parse_resource_dump(text):
    """Parses `aapt2 dump resources` text.

    Returns {"type/name": {"id": "0x...", "configs": {config: value_text}}}.
    Value lines under a resource belong to the current "config" line, or to the default config
    (empty string) when no config line has been seen.
    """
    resources = {}
    current = None
    resource_indent = 0
    config = ""
    for line in text.splitlines():
        match = RESOURCE_LINE.match(line)
        if match:
            resource_indent = len(match.group(1))
            name = match.group(3)
            current = resources.setdefault(name, {"id": match.group(2).lower(), "configs": {}})
            config = ""
            continue
        stripped = line.strip()
        if not stripped:
            continue
        indent = len(line) - len(line.lstrip())
        if indent <= resource_indent:
            current = None  # a package or type header, or the end of the resource block
            continue
        if current is None:
            continue
        config_match = CONFIG_LINE.match(stripped)
        if config_match:
            config = config_match.group(1).strip()
            current["configs"].setdefault(config, [])
            continue
        current["configs"].setdefault(config, []).append(stripped)
    return {
        name: {
            "id": entry["id"],
            "configs": {cfg: "\n".join(lines) for cfg, lines in entry["configs"].items()},
        }
        for name, entry in resources.items()
    }


def run_aapt2_dump(apk, build_tools):
    aapt2 = os.path.join(build_tools, "aapt2.exe" if os.name == "nt" else "aapt2")
    result = subprocess.run(
        [aapt2, "dump", "resources", apk],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    if result.returncode != 0:
        raise RuntimeError(f"aapt2 dump failed for {apk}: {result.stderr.strip()}")
    return result.stdout


def read_resources(apk, build_tools):
    with zipfile.ZipFile(apk) as zf:
        if "resources.arsc" not in zf.namelist():
            return {}
    return parse_resource_dump(run_aapt2_dump(apk, build_tools))


# ---------------------------------------------------------------- signing

def read_signing(apk, build_tools):
    """Returns (verifies, certificate lines) from apksigner verify --print-certs."""
    apksigner_jar = os.path.join(build_tools, "lib", "apksigner.jar")
    result = subprocess.run(
        ["java", "-jar", apksigner_jar, "verify", "--print-certs", apk],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    lines = [line.strip() for line in result.stdout.splitlines() if "certificate" in line.lower()]
    return result.returncode == 0, sorted(lines)


# ---------------------------------------------------------------- comparison

def compare_zip(a, b, out, info):
    names_a, names_b = set(a["zip"]), set(b["zip"])
    for name in sorted(names_a - names_b):
        out.append(Difference("zip.removed", name))
    for name in sorted(names_b - names_a):
        out.append(Difference("zip.added", name))
    for name in sorted(names_a & names_b):
        ea, eb = a["zip"][name], b["zip"][name]
        if ea["sha256"] is not None and eb["sha256"] is not None and ea["sha256"] != eb["sha256"]:
            out.append(Difference("zip.changed", name))
        if ea["stored"] != eb["stored"]:
            info["zip.compression"] += 1
        if ea["align4"] != eb["align4"] or (ea["is_lib_so"] and ea["align16k"] != eb["align16k"]):
            info["zip.alignment"] += 1
    info["zip.entries"] = f"{len(names_a)} / {len(names_b)}"


def compare_dex(a, b, out, info):
    classes_a, classes_b = a["dex"], b["dex"]
    for descriptor in sorted(set(classes_a) - set(classes_b)):
        out.append(Difference("dex.removed", descriptor, f"was in {classes_a[descriptor]}"))
    for descriptor in sorted(set(classes_b) - set(classes_a)):
        out.append(Difference("dex.added", descriptor, f"now in {classes_b[descriptor]}"))
    info["dex.moved"] = sum(
        1 for d in set(classes_a) & set(classes_b) if classes_a[d] != classes_b[d]
    )
    info["dex.classes"] = f"{len(classes_a)} / {len(classes_b)}"


def compare_resources(a, b, out, info):
    res_a, res_b = a["arsc"], b["arsc"]
    for name in sorted(set(res_a) - set(res_b)):
        out.append(Difference("arsc.removed", name, res_a[name]["id"]))
    for name in sorted(set(res_b) - set(res_a)):
        out.append(Difference("arsc.added", name, res_b[name]["id"]))
    for name in sorted(set(res_a) & set(res_b)):
        ra, rb = res_a[name], res_b[name]
        if ra["id"] != rb["id"]:
            out.append(Difference("arsc.id.changed", name, f"{ra['id']} -> {rb['id']}"))
        if ra["configs"] != rb["configs"]:
            configs_a, configs_b = set(ra["configs"]), set(rb["configs"])
            detail = (
                f"configs +{len(configs_b - configs_a)} -{len(configs_a - configs_b)}, "
                f"values {sum(1 for c in configs_a & configs_b if ra['configs'][c] != rb['configs'][c])} changed"
            )
            out.append(Difference("arsc.value.changed", name, detail))
    info["arsc.entries"] = f"{len(res_a)} / {len(res_b)}"


def compare_signing(a, b, out, info):
    if a["verifies"] != b["verifies"]:
        out.append(Difference("signing.verify", "apksigner", f"{a['verifies']} -> {b['verifies']}"))
    if a["certs"] != b["certs"]:
        out.append(Difference("signing.cert", "apksigner", "certificate lines differ"))
    if a.get("sig_files") is not None and b.get("sig_files") is not None:
        names = set(a["sig_files"]) | set(b["sig_files"])
        info["signing.files"] = sum(1 for n in names if a["sig_files"].get(n) != b["sig_files"].get(n))


def load_allow_list(path):
    rules = []
    if not path:
        return rules
    with open(path, encoding="utf-8") as f:
        for raw in f:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split(None, 1)
            if len(parts) != 2:
                raise ValueError(f"bad allow-list line: {line!r}")
            rules.append((parts[0], parts[1]))
    return rules


def is_allowed(diff, rules):
    return any(
        fnmatch.fnmatchcase(diff.category, cat) and fnmatch.fnmatchcase(diff.subject, subj)
        for cat, subj in rules
    )


def load_apk(path, build_tools, with_signing):
    apk = {
        "zip": read_zip(path),
        "dex": read_dex_classes(path),
        "arsc": read_resources(path, build_tools),
    }
    if with_signing:
        verifies, certs = read_signing(path, build_tools)
        apk.update({"verifies": verifies, "certs": certs, "sig_files": read_signature_files(path)})
    return apk


def compare_apks(a, b, rules=()):
    """Compares two loaded APKs. Returns (differences, allowed, info)."""
    out = []
    info = defaultdict(int)
    compare_zip(a, b, out, info)
    compare_dex(a, b, out, info)
    compare_resources(a, b, out, info)
    if "verifies" in a and "verifies" in b:
        compare_signing(a, b, out, info)
    allowed = [d for d in out if is_allowed(d, rules)]
    unallowed = [d for d in out if not is_allowed(d, rules)]
    return unallowed, allowed, info


def summarize(unallowed, allowed, info, limit):
    lines = []
    for label, group in (("NOT ALLOWED", unallowed), ("allowed", allowed)):
        counts = Counter(d.category for d in group)
        lines.append(f"{label}: {len(group)}")
        for category in sorted(counts):
            lines.append(f"  {category}: {counts[category]}")
            shown = [d for d in group if d.category == category][:limit]
            for d in shown:
                suffix = f" ({d.detail})" if d.detail else ""
                lines.append(f"    {d.subject}{suffix}")
            if counts[category] > limit:
                lines.append(f"    ... {counts[category] - limit} more")
    lines.append("informational:")
    for key in sorted(info):
        lines.append(f"  {key}: {info[key]}")
    return "\n".join(lines)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("apk_a")
    parser.add_argument("apk_b")
    parser.add_argument("--allow", help="allow-list file (format in the module docstring)")
    parser.add_argument("--build-tools", default=DEFAULT_BUILD_TOOLS, help="SDK build-tools 36.0.0 directory")
    parser.add_argument("--no-signing", action="store_true", help="skip apksigner (for unsigned test APKs)")
    parser.add_argument("--limit", type=int, default=20, help="examples shown per category")
    parser.add_argument("--json", metavar="FILE", help="write all differences as JSON")
    args = parser.parse_args(argv)

    try:
        rules = load_allow_list(args.allow)
    except (OSError, ValueError) as error:
        print(f"allow-list error: {error}", file=sys.stderr)
        return 2
    for path in (args.apk_a, args.apk_b):
        if not os.path.isfile(path):
            print(f"not a file: {path}", file=sys.stderr)
            return 2

    with_signing = not args.no_signing
    a = load_apk(args.apk_a, args.build_tools, with_signing)
    b = load_apk(args.apk_b, args.build_tools, with_signing)
    unallowed, allowed, info = compare_apks(a, b, rules)
    print(summarize(unallowed, allowed, info, args.limit))

    if args.json:
        with open(args.json, "w", encoding="utf-8") as f:
            json.dump(
                {
                    "not_allowed": [d.as_dict() for d in unallowed],
                    "allowed": [d.as_dict() for d in allowed],
                    "informational": dict(info),
                },
                f,
                indent=2,
            )
    return 1 if unallowed else 0


if __name__ == "__main__":
    sys.exit(main())
