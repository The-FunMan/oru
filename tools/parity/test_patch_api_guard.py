"""Unit tests for patch_api_guard.py. Class files are assembled in code; no real bundles are used.

Run from the repository root:
    python -I -m unittest tools/parity/test_patch_api_guard.py
"""

import contextlib
import io
import os
import struct
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import patch_api_guard as guard  # noqa: E402

TAG_FIELDREF = 9
TAG_METHODREF = 10


class _Pool:
    """Minimal constant pool writer that de-duplicates entries."""

    def __init__(self) -> None:
        self.count = 1
        self.blob = bytearray()
        self.cache: dict[tuple, int] = {}

    def _add(self, key: tuple, data: bytes) -> int:
        if key not in self.cache:
            self.cache[key] = self.count
            self.blob += data
            self.count += 1
        return self.cache[key]

    def utf8(self, text: str) -> int:
        raw = text.encode()
        return self._add(("utf8", text), b"\x01" + struct.pack(">H", len(raw)) + raw)

    def cls(self, name: str) -> int:
        return self._add(("class", name), b"\x07" + struct.pack(">H", self.utf8(name)))

    def member(self, tag: int, owner: str, name: str, desc: str) -> int:
        nat = self._add(("nat", name, desc), b"\x0c" + struct.pack(">HH", self.utf8(name), self.utf8(desc)))
        return self._add(("ref", tag, owner, name, desc), bytes([tag]) + struct.pack(">HH", self.cls(owner), nat))

    def long_constant(self) -> None:
        self.blob += b"\x05" + bytes(8)
        self.count += 2  # Long takes two slots


def build_class(
    name: str,
    *,
    super_name: str | None = "java/lang/Object",
    fields: tuple = (),
    methods: tuple = (),
    class_refs: tuple = (),
    member_refs: tuple = (),
    long_constant: bool = False,
) -> bytes:
    """Assemble a class file. member_refs items are (kind, owner, name, desc) with kind 'field' or 'method'."""
    pool = _Pool()
    this_idx = pool.cls(name)
    super_idx = pool.cls(super_name) if super_name else 0
    if long_constant:
        pool.long_constant()
    for ref in class_refs:
        pool.cls(ref)
    for kind, owner, ref_name, desc in member_refs:
        pool.member(TAG_FIELDREF if kind == "field" else TAG_METHODREF, owner, ref_name, desc)

    def member_table(items: tuple) -> bytes:
        out = struct.pack(">H", len(items))
        for item_name, item_desc in items:
            out += struct.pack(">HHHH", 0x0001, pool.utf8(item_name), pool.utf8(item_desc), 0)
        return out

    body = struct.pack(">4H", 0x0021, this_idx, super_idx, 0)
    body += member_table(fields) + member_table(methods) + struct.pack(">H", 0)
    header = b"\xca\xfe\xba\xbe" + struct.pack(">HHH", 0, 65, pool.count)
    return header + bytes(pool.blob) + body


def patcher_classes() -> dict:
    classes = [
        guard.parse_class(build_class(
            "app/morphe/patcher/A",
            fields=(("x", "I"),),
            methods=(("run", "()V"),),
        )),
        guard.parse_class(build_class("app/morphe/patcher/Base", methods=(("base", "()V"),))),
        guard.parse_class(build_class(
            "app/morphe/patcher/Child",
            super_name="app/morphe/patcher/Base",
            methods=(("child", "()V"),),
        )),
    ]
    return {cf.name: cf for cf in classes}


def run_check(bundle_class_bytes: list) -> list:
    bundle = [guard.parse_class(data) for data in bundle_class_bytes]
    stats = guard.Stats()
    return guard.check(patcher_classes(), bundle, stats)


def labels(findings: list) -> set:
    return {(f.kind, f.label) for f in findings}


class CheckTests(unittest.TestCase):
    def test_resolved_references_pass(self) -> None:
        data = build_class(
            "patches/Ok",
            member_refs=(
                ("method", "app/morphe/patcher/A", "run", "()V"),
                ("field", "app/morphe/patcher/A", "x", "I"),
                ("method", "java/lang/String", "length", "()I"),
                ("method", "app/morphe/patcher/A", "hashCode", "()I"),
            ),
        )
        self.assertEqual(run_check([data]), [])

    def test_inherited_method_resolves_through_patcher_superclass(self) -> None:
        data = build_class(
            "patches/Inherited",
            member_refs=(("method", "app/morphe/patcher/Child", "base", "()V"),),
        )
        self.assertEqual(run_check([data]), [])

    def test_missing_class_fails(self) -> None:
        data = build_class(
            "patches/MissingClass",
            member_refs=(("method", "app/morphe/patcher/Gone", "foo", "()V"),),
        )
        findings = run_check([data])
        self.assertEqual(labels(findings), {("class", "app/morphe/patcher/Gone")})
        self.assertEqual(findings[0].users, {"patches.MissingClass"})
        self.assertEqual(findings[0].members, {"method foo()V"})

    def test_missing_class_in_descriptor_fails(self) -> None:
        data = build_class(
            "patches/Descriptor",
            member_refs=(("method", "app/morphe/patcher/A", "run", "(Lapp/morphe/patcher/Gone;)V"),),
        )
        self.assertEqual(
            labels(run_check([data])),
            {("class", "app/morphe/patcher/Gone"), ("method", "app/morphe/patcher/A.run(Lapp/morphe/patcher/Gone;)V")},
        )

    def test_missing_method_fails(self) -> None:
        data = build_class(
            "patches/MissingMethod",
            member_refs=(("method", "app/morphe/patcher/A", "nope", "()V"),),
        )
        self.assertEqual(labels(run_check([data])), {("method", "app/morphe/patcher/A.nope()V")})

    def test_missing_field_fails(self) -> None:
        data = build_class(
            "patches/MissingField",
            member_refs=(("field", "app/morphe/patcher/A", "y", "I"),),
        )
        self.assertEqual(labels(run_check([data])), {("field", "app/morphe/patcher/A.yI")})

    def test_long_constant_takes_two_slots(self) -> None:
        data = build_class(
            "patches/WithLong",
            long_constant=True,
            member_refs=(("method", "app/morphe/patcher/Gone", "foo", "()V"),),
        )
        self.assertEqual(labels(run_check([data])), {("class", "app/morphe/patcher/Gone")})


class CliTests(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.patcher_jar = os.path.join(self.tmp.name, "patcher.jar")
        with zipfile.ZipFile(self.patcher_jar, "w") as zf:
            zf.writestr("app/morphe/patcher/A.class", build_class("app/morphe/patcher/A", methods=(("run", "()V"),)))
            zf.writestr("app/morphe/patcher/Base.class", build_class("app/morphe/patcher/Base"))

    def _bundle(self, name: str, entries: dict) -> str:
        path = os.path.join(self.tmp.name, name)
        with zipfile.ZipFile(path, "w") as zf:
            for entry, data in entries.items():
                zf.writestr(entry, data)
        return path

    def _run(self, *args: str) -> tuple:
        out = io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(io.StringIO()):
            code = guard.main(list(args))
        return code, out.getvalue()

    def test_exit_codes(self) -> None:
        clean = self._bundle("clean.mpp", {"patches/Ok.class": build_class(
            "patches/Ok", member_refs=(("method", "app/morphe/patcher/A", "run", "()V"),))})
        broken = self._bundle("broken.mpp", {"patches/Bad.class": build_class(
            "patches/Bad", member_refs=(("method", "app/morphe/patcher/A", "gone", "()V"),))})
        dex_only = self._bundle("dex.mpp", {"extensions/x.mpe": b"dex\n035\x00" + bytes(16)})

        code, out = self._run("--patcher", self.patcher_jar, "--bundle", clean)
        self.assertEqual(code, 0)
        self.assertIn("unresolved classes=0 fields=0 methods=0", out)

        code, out = self._run("--patcher", self.patcher_jar, "--bundle", broken)
        self.assertEqual(code, 1)
        self.assertIn("UNRESOLVED method app/morphe/patcher/A.gone()V", out)

        code, _ = self._run("--patcher", self.patcher_jar, "--bundle", dex_only)
        self.assertEqual(code, 2)

    def test_patcher_directory_input(self) -> None:
        classes_dir = os.path.join(self.tmp.name, "classes", "app", "morphe", "patcher")
        os.makedirs(classes_dir)
        with open(os.path.join(classes_dir, "A.class"), "wb") as handle:
            handle.write(build_class("app/morphe/patcher/A", methods=(("run", "()V"),)))
        bundle = self._bundle("dir.mpp", {"patches/Ok.class": build_class(
            "patches/Ok", member_refs=(("method", "app/morphe/patcher/A", "run", "()V"),))})
        code, _ = self._run("--patcher", os.path.join(self.tmp.name, "classes"), "--bundle", bundle)
        self.assertEqual(code, 0)


if __name__ == "__main__":
    unittest.main()
