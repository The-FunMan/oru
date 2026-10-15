#!/usr/bin/env python3
"""Check that patch bundles only use app/morphe/patcher API that the patcher build provides.

Patches ship as .mpp zips of JVM classes that run against the patcher at runtime. Removing a public
patcher class, method or field compiles fine but fails only when a bundle loads (NoClassDefFoundError,
NoSuchMethodError). This tool finds such references ahead of time by reading constant pools only.

Scope: class files (including classes inside nested .jar entries). Dex entries such as extensions/*.mpe
are counted and reported, not scanned.

Usage:
    python -I tools/parity/patch_api_guard.py --patcher <patcher.jar|classes dir> --bundle <patches.mpp> [--bundle ...]

Exit codes: 0 clean, 1 unresolved references, 2 usage error or nothing to check.
"""

from __future__ import annotations

import argparse
import dataclasses
import io
import os
import struct
import sys
import zipfile
from collections.abc import Iterable, Iterator

PREFIX = "app/morphe/patcher/"
CLASS_MAGIC = b"\xca\xfe\xba\xbe"
DEX_MAGIC = b"dex\n"

TAG_UTF8 = 1
TAG_INT, TAG_FLOAT = 3, 4
TAG_LONG, TAG_DOUBLE = 5, 6
TAG_CLASS = 7
TAG_FIELDREF, TAG_METHODREF, TAG_IFACE_METHODREF = 9, 10, 11
TAG_NAME_AND_TYPE = 12
TAG_METHOD_HANDLE = 15
TAG_METHOD_TYPE = 16

# Tags whose payload is one or two big-endian u2 indexes.
_INDEX_TAGS = {7, 8, 9, 10, 11, 12, 16, 17, 18, 19, 20}
_ONE_INDEX_TAGS = {7, 8, 16, 19, 20}
_WIDE_TAGS = {TAG_LONG, TAG_DOUBLE}


@dataclasses.dataclass(frozen=True)
class MemberRef:
    kind: str  # "field" or "method"
    owner: str
    name: str
    desc: str


@dataclasses.dataclass
class ClassFile:
    name: str
    super_name: str | None
    interfaces: tuple[str, ...]
    fields: frozenset[tuple[str, str]]
    methods: frozenset[tuple[str, str]]
    class_refs: frozenset[str]
    member_refs: frozenset[MemberRef]


@dataclasses.dataclass
class Finding:
    kind: str  # "class", "field" or "method"
    label: str
    users: set[str] = dataclasses.field(default_factory=set)
    # Only for kind "class": member references into a missing class, folded under it.
    members: set[str] = dataclasses.field(default_factory=set)


@dataclasses.dataclass
class BundleScan:
    path: str
    classes: list[ClassFile]
    dex_entries: int


@dataclasses.dataclass
class Stats:
    patcher_classes: int = 0
    bundle_classes: int = 0
    class_refs: int = 0
    member_refs: int = 0
    missing_supertypes: set[str] = dataclasses.field(default_factory=set)
    external_supertypes: set[str] = dataclasses.field(default_factory=set)


def classes_in_descriptor(desc: str) -> Iterator[str]:
    """Yield class names from a field or method descriptor, e.g. (ILfoo/Bar;)V -> foo/Bar."""
    pos = desc.find("L")
    while pos >= 0:
        end = desc.find(";", pos)
        if end < 0:
            return
        yield desc[pos + 1 : end]
        pos = desc.find("L", end)


def _read_pool(data: bytes) -> tuple[dict[int, str], dict[int, tuple[int, ...]], int]:
    """Return (utf8 entries, other entries as (tag, indexes...), offset after the pool)."""
    (count,) = struct.unpack_from(">H", data, 8)
    utf8: dict[int, str] = {}
    pool: dict[int, tuple[int, ...]] = {}
    pos = 10
    index = 1
    while index < count:
        tag = data[pos]
        pos += 1
        if tag == TAG_UTF8:
            (length,) = struct.unpack_from(">H", data, pos)
            raw = data[pos + 2 : pos + 2 + length]
            # Modified UTF-8 encodes NUL as C0 80; class and member names are ASCII in practice.
            utf8[index] = raw.replace(b"\xc0\x80", b"\x00").decode("utf-8", "replace")
            pos += 2 + length
        elif tag in (TAG_INT, TAG_FLOAT):
            pos += 4
        elif tag in _WIDE_TAGS:
            pos += 8
            index += 1  # Long and Double occupy two constant pool slots
        elif tag == TAG_METHOD_HANDLE:
            pool[index] = (tag, data[pos], struct.unpack_from(">H", data, pos + 1)[0])
            pos += 3
        elif tag in _INDEX_TAGS:
            width = 1 if tag in _ONE_INDEX_TAGS else 2
            pool[index] = (tag,) + struct.unpack_from(">" + "H" * width, data, pos)
            pos += 2 * width
        else:
            raise ValueError(f"unsupported constant pool tag {tag} at entry {index}")
        index += 1
    return utf8, pool, pos


def _read_members(data: bytes, pos: int, utf8: dict[int, str]) -> tuple[frozenset[tuple[str, str]], int]:
    (count,) = struct.unpack_from(">H", data, pos)
    pos += 2
    members: set[tuple[str, str]] = set()
    for _ in range(count):
        _access, name_idx, desc_idx, attr_count = struct.unpack_from(">4H", data, pos)
        pos += 8
        members.add((utf8[name_idx], utf8[desc_idx]))
        for _ in range(attr_count):
            (length,) = struct.unpack_from(">I", data, pos + 2)
            pos += 6 + length
    return frozenset(members), pos


def parse_class(data: bytes) -> ClassFile:
    if data[:4] != CLASS_MAGIC:
        raise ValueError("missing class file magic")
    utf8, pool, pos = _read_pool(data)

    _access, this_idx, super_idx, iface_count = struct.unpack_from(">4H", data, pos)
    pos += 8
    iface_idxs = struct.unpack_from(f">{iface_count}H", data, pos)
    pos += 2 * iface_count
    fields, pos = _read_members(data, pos, utf8)
    methods, pos = _read_members(data, pos, utf8)

    descriptors: set[str] = set()
    plain_classes: set[str] = set()
    for entry in pool.values():
        if entry[0] == TAG_CLASS:
            name = utf8[entry[1]]
            if name.startswith("["):
                descriptors.add(name)
            else:
                plain_classes.add(name)
        elif entry[0] == TAG_NAME_AND_TYPE:
            descriptors.add(utf8[entry[2]])
        elif entry[0] == TAG_METHOD_TYPE:
            descriptors.add(utf8[entry[1]])
    class_refs = set(plain_classes)
    for desc in descriptors:
        class_refs.update(classes_in_descriptor(desc))

    member_refs: set[MemberRef] = set()
    for entry in pool.values():
        if entry[0] not in (TAG_FIELDREF, TAG_METHODREF, TAG_IFACE_METHODREF):
            continue
        _, owner_idx, nat_idx = entry
        _, name_idx, desc_idx = pool[nat_idx]
        kind = "field" if entry[0] == TAG_FIELDREF else "method"
        member_refs.add(MemberRef(kind, utf8[pool[owner_idx][1]], utf8[name_idx], utf8[desc_idx]))

    return ClassFile(
        name=utf8[pool[this_idx][1]],
        super_name=utf8[pool[super_idx][1]] if super_idx else None,
        interfaces=tuple(utf8[pool[i][1]] for i in iface_idxs),
        fields=fields,
        methods=methods,
        class_refs=frozenset(class_refs),
        member_refs=frozenset(member_refs),
    )


def _zip_files(zf: zipfile.ZipFile, origin: str) -> Iterator[tuple[str, bytes]]:
    """Yield (origin, bytes) for every file in a zip, descending into nested .jar entries."""
    for info in zf.infolist():
        if info.is_dir():
            continue
        data = zf.read(info)
        if info.filename.endswith(".jar"):
            with zipfile.ZipFile(io.BytesIO(data)) as inner:
                yield from _zip_files(inner, f"{origin}!{info.filename}")
        else:
            yield f"{origin}!{info.filename}", data


def scan_bundle(path: str) -> BundleScan:
    classes: list[ClassFile] = []
    dex_entries = 0
    with zipfile.ZipFile(path) as zf:
        for origin, data in _zip_files(zf, path):
            if origin.endswith(".class"):
                try:
                    classes.append(parse_class(data))
                except (ValueError, struct.error, KeyError) as error:
                    raise ValueError(f"{origin}: cannot parse class file ({error})") from error
            elif data[:4] == DEX_MAGIC:
                dex_entries += 1
    return BundleScan(path=path, classes=classes, dex_entries=dex_entries)


def load_patcher(path: str) -> dict[str, ClassFile]:
    """Parse every class of the patcher jar or classes directory, skipping META-INF."""
    blobs: list[tuple[str, bytes]] = []
    if os.path.isdir(path):
        for root, _dirs, files in os.walk(path):
            for filename in files:
                if not filename.endswith(".class"):
                    continue
                full = os.path.join(root, filename)
                rel = os.path.relpath(full, path).replace(os.sep, "/")
                if rel.startswith("META-INF/"):
                    continue
                with open(full, "rb") as handle:
                    blobs.append((rel, handle.read()))
    else:
        with zipfile.ZipFile(path) as zf:
            for info in zf.infolist():
                if info.filename.endswith(".class") and not info.filename.startswith("META-INF/"):
                    blobs.append((info.filename, zf.read(info)))

    classes: dict[str, ClassFile] = {}
    for origin, data in blobs:
        try:
            parsed = parse_class(data)
        except (ValueError, struct.error, KeyError) as error:
            raise ValueError(f"patcher {origin}: cannot parse class file ({error})") from error
        classes.setdefault(parsed.name, parsed)
    return classes


OBJECT_CLASS = "java/lang/Object"
# Public and protected instance methods of java.lang.Object that every patcher class inherits.
OBJECT_METHODS = frozenset({
    ("equals", "(Ljava/lang/Object;)Z"),
    ("hashCode", "()I"),
    ("toString", "()Ljava/lang/String;"),
    ("getClass", "()Ljava/lang/Class;"),
    ("notify", "()V"),
    ("notifyAll", "()V"),
    ("wait", "()V"),
    ("wait", "(J)V"),
    ("wait", "(JI)V"),
    ("clone", "()Ljava/lang/Object;"),
    ("finalize", "()V"),
})


class Resolver:
    """Answers whether a member exists on a patcher class or a supertype that is also in the patcher.

    Object is checked against OBJECT_METHODS. Any other supertype outside the patcher is not
    inspected; a lookup that reaches one is assumed to resolve and the supertype is listed in the report.
    """

    def __init__(self, patcher: dict[str, ClassFile], stats: Stats) -> None:
        self.patcher = patcher
        self.stats = stats

    def has_member(self, owner: str, name: str, desc: str, kind: str) -> bool:
        pending = [owner]
        seen: set[str] = set()
        while pending:
            current = pending.pop()
            if current in seen:
                continue
            seen.add(current)
            if current == OBJECT_CLASS:
                if kind == "method" and (name, desc) in OBJECT_METHODS:
                    return True
                continue
            if not current.startswith(PREFIX):
                self.stats.external_supertypes.add(current)
                return True
            cf = self.patcher.get(current)
            if cf is None:
                # A patcher class extends or implements something the patcher does not contain.
                # That is a patcher defect; record it and assume the lookup resolves.
                self.stats.missing_supertypes.add(current)
                return True
            declared = cf.fields if kind == "field" else cf.methods
            if (name, desc) in declared:
                return True
            if cf.super_name:
                pending.append(cf.super_name)
            pending.extend(cf.interfaces)
        return False


def check(patcher: dict[str, ClassFile], bundle_classes: Iterable[ClassFile], stats: Stats) -> list[Finding]:
    resolver = Resolver(patcher, stats)
    findings: dict[tuple[str, str], Finding] = {}

    def finding(kind: str, label: str) -> Finding:
        return findings.setdefault((kind, label), Finding(kind, label))

    referenced_classes: set[str] = set()
    referenced_members: set[MemberRef] = set()
    for cf in bundle_classes:
        stats.bundle_classes += 1
        user = cf.name.replace("/", ".")
        for name in cf.class_refs:
            if not name.startswith(PREFIX):
                continue
            referenced_classes.add(name)
            if name not in patcher:
                finding("class", name).users.add(user)
        for ref in cf.member_refs:
            if not ref.owner.startswith(PREFIX):
                continue
            referenced_members.add(ref)
            if ref.owner not in patcher:
                # The owner's class finding already lists this bundle class as a user.
                finding("class", ref.owner).members.add(f"{ref.kind} {ref.name}{ref.desc}")
                continue
            if not resolver.has_member(ref.owner, ref.name, ref.desc, ref.kind):
                finding(ref.kind, f"{ref.owner}.{ref.name}{ref.desc}").users.add(user)

    stats.class_refs = len(referenced_classes)
    stats.member_refs = len(referenced_members)
    order = {"class": 0, "field": 1, "method": 2}
    return sorted(findings.values(), key=lambda f: (order[f.kind], f.label))


def _format_users(users: set[str]) -> str:
    shown = sorted(users)[:3]
    extra = len(users) - len(shown)
    text = ", ".join(shown) if shown else "(none)"
    return f"{text} (+{extra} more)" if extra else text


def _print_findings(findings: list[Finding]) -> None:
    for item in findings:
        print(f"UNRESOLVED {item.kind:<6} {item.label}")
        print(f"    used by: {_format_users(item.users)}")
        if item.members:
            print(f"    members on this missing class: {', '.join(sorted(item.members))}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Check patch bundles against the patcher's public API.")
    parser.add_argument("--patcher", required=True, help="patcher jar or directory of .class files")
    parser.add_argument("--bundle", required=True, action="append", help="patches .mpp bundle; repeat for more")
    args = parser.parse_args(argv)

    try:
        patcher = load_patcher(args.patcher)
        scans = [scan_bundle(path) for path in args.bundle]
    except (OSError, ValueError, zipfile.BadZipFile) as error:
        print(f"error: {error}", file=sys.stderr)
        return 2

    for scan in scans:
        if not scan.classes:
            if scan.dex_entries:
                print(f"error: {scan.path} contains only dex files; this tool checks class files only", file=sys.stderr)
            else:
                print(f"error: {scan.path} contains no class files", file=sys.stderr)
            return 2
        print(f"scanned {scan.path}: {len(scan.classes)} class files, {scan.dex_entries} dex entries not scanned")

    stats = Stats(patcher_classes=len(patcher))
    findings = check(patcher, (cf for scan in scans for cf in scan.classes), stats)
    _print_findings(findings)

    if stats.missing_supertypes:
        print(f"note: patcher is missing supertypes {', '.join(sorted(stats.missing_supertypes))}; "
              "lookups through them were assumed to resolve")
    if stats.external_supertypes:
        print(f"note: lookups through non-Object supertypes outside the patcher were assumed to resolve: "
              f"{', '.join(sorted(stats.external_supertypes))}")
    unresolved = {kind: sum(1 for f in findings if f.kind == kind) for kind in ("class", "field", "method")}
    print(
        f"summary: patcher classes={stats.patcher_classes} bundle classes={stats.bundle_classes} "
        f"patcher class refs={stats.class_refs} member refs={stats.member_refs} "
        f"unresolved classes={unresolved['class']} fields={unresolved['field']} methods={unresolved['method']}"
    )
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
