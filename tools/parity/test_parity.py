"""Unit tests for parity.py. Every APK is generated here; no real app files are used.

Run from the repository root:
    python -m unittest discover -s tools/parity -p "test_*.py" -v
"""

import contextlib
import io
import os
import shutil
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import parity  # noqa: E402

DEFAULT_ALLOW = ()


def uleb128(value):
    out = bytearray()
    while True:
        byte = value & 0x7F
        value >>= 7
        if value:
            out.append(byte | 0x80)
        else:
            out.append(byte)
            return bytes(out)


def make_dex(descriptors):
    """A minimal dex file whose class_defs list the given descriptors, in order."""
    strings = []
    for descriptor in descriptors:
        if descriptor not in strings:
            strings.append(descriptor)
    string_ids_off = 0x70
    type_ids_off = string_ids_off + 4 * len(strings)
    class_defs_off = type_ids_off + 4 * len(strings)
    data_off = class_defs_off + 32 * len(descriptors)

    data = bytearray()
    string_offsets = []
    for text in strings:
        encoded = text.encode("utf-8")
        string_offsets.append(data_off + len(data))
        data += uleb128(len(text)) + encoded + b"\x00"

    body = bytearray()
    body += b"".join(struct.pack("<I", off) for off in string_offsets)
    body += b"".join(struct.pack("<I", i) for i in range(len(strings)))
    for descriptor in descriptors:
        class_idx = strings.index(descriptor)
        body += struct.pack("<I", class_idx) + b"\x00" * 28

    header = bytearray(0x70)
    header[0:8] = b"dex\n035\x00"
    struct.pack_into("<I", header, 32, 0x70 + len(body) + len(data))  # file_size
    struct.pack_into("<I", header, 36, 0x70)  # header_size
    struct.pack_into("<I", header, 40, 0x12345678)  # endian_tag
    struct.pack_into("<II", header, 56, len(strings), string_ids_off)
    struct.pack_into("<II", header, 64, len(strings), type_ids_off)
    struct.pack_into("<II", header, 96, len(descriptors), class_defs_off)
    struct.pack_into("<II", header, 104, len(data), data_off)
    return bytes(header) + bytes(body) + bytes(data)


def make_apk(path, entries, stored=()):
    """entries: {name: bytes}. Names in `stored` are written uncompressed."""
    with zipfile.ZipFile(path, "w") as zf:
        for name, content in entries.items():
            method = zipfile.ZIP_STORED if name in stored else zipfile.ZIP_DEFLATED
            zf.writestr(zipfile.ZipInfo(name), content, compress_type=method)
    return path


def base_entries(**overrides):
    entries = {
        "AndroidManifest.xml": b"manifest",
        "res/layout/main.xml": b"<layout/>",
        "assets/data.bin": b"data",
        "classes.dex": make_dex(["Lcom/example/A;", "Lcom/example/B;"]),
        "META-INF/MANIFEST.MF": b"Manifest-Version: 1.0\n",
        "META-INF/CERT.SF": b"sf-one",
        "META-INF/CERT.RSA": b"rsa-one",
    }
    entries.update(overrides)
    return {k: v for k, v in entries.items() if v is not None}


def compare_files(a_path, b_path, rules=DEFAULT_ALLOW, with_signing=False):
    a = {
        "zip": parity.read_zip(a_path),
        "dex": parity.read_dex_classes(a_path),
        "arsc": {},
    }
    b = {
        "zip": parity.read_zip(b_path),
        "dex": parity.read_dex_classes(b_path),
        "arsc": {},
    }
    return parity.compare_apks(a, b, rules)


def categories(diffs):
    return sorted(d.category for d in diffs)


class DexReaderTest(unittest.TestCase):
    def test_reads_descriptors_in_class_order(self):
        dex = make_dex(["Lcom/example/B;", "Lcom/example/A;"])
        self.assertEqual(parity.dex_class_descriptors(dex), ["Lcom/example/B;", "Lcom/example/A;"])

    def test_handles_multibyte_utf8_and_long_strings(self):
        long_name = "Lcom/" + "x" * 300 + "/Y;"
        dex = make_dex(["Lcom/café/Z;", long_name])
        self.assertEqual(parity.dex_class_descriptors(dex), ["Lcom/café/Z;", long_name])

    def test_rejects_non_dex(self):
        with self.assertRaises(ValueError):
            parity.dex_class_descriptors(b"not a dex file at all, just bytes")


class ZipComparisonTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp(prefix="parity-test-")

    def tearDown(self):
        shutil.rmtree(self.dir, ignore_errors=True)

    def path(self, name):
        return os.path.join(self.dir, name)

    def test_identical_apks_have_no_differences(self):
        a = make_apk(self.path("a.apk"), base_entries())
        b = make_apk(self.path("b.apk"), base_entries())
        unallowed, allowed, _ = compare_files(a, b)
        self.assertEqual(unallowed, [])
        self.assertEqual(allowed, [])

    def test_added_removed_and_changed_entries(self):
        a = make_apk(self.path("a.apk"), base_entries())
        b = make_apk(
            self.path("b.apk"),
            base_entries(**{"res/layout/main.xml": b"<layout>changed</layout>", "assets/data.bin": None,
                            "lib/x86/libnew.so": b"so"}),
        )
        unallowed, _, _ = compare_files(a, b)
        self.assertEqual(
            sorted((d.category, d.subject) for d in unallowed),
            [
                ("zip.added", "lib/x86/libnew.so"),
                ("zip.changed", "res/layout/main.xml"),
                ("zip.removed", "assets/data.bin"),
            ],
        )

    def test_signature_files_are_not_hashed(self):
        a = make_apk(self.path("a.apk"), base_entries())
        b = make_apk(
            self.path("b.apk"),
            base_entries(**{"META-INF/CERT.RSA": b"rsa-two", "META-INF/CERT.SF": b"sf-two"}),
        )
        unallowed, _, _ = compare_files(a, b)
        self.assertEqual(unallowed, [])

    def test_compression_and_alignment_are_informational(self):
        a = make_apk(self.path("a.apk"), base_entries(), stored=("assets/data.bin",))
        b = make_apk(self.path("b.apk"), base_entries())
        unallowed, allowed, info = compare_files(a, b)
        self.assertEqual(unallowed, [])
        self.assertEqual(allowed, [])
        self.assertGreaterEqual(info["zip.compression"], 1)

    def test_allow_list_suppresses_matching_differences_only(self):
        a = make_apk(self.path("a.apk"), base_entries())
        b = make_apk(self.path("b.apk"), base_entries(**{"res/layout/main.xml": b"other"}, **{"x/new.txt": b"n"}))
        rules = [("zip.added", "x/*"), ("zip.changed", "res/layout/*")]
        unallowed, allowed, _ = compare_files(a, b, rules)
        self.assertEqual(unallowed, [])
        self.assertEqual(categories(allowed), ["zip.added", "zip.changed"])

    def test_dex_class_sets_across_files(self):
        def two_dex(second_classes):
            return base_entries(**{"classes2.dex": make_dex(second_classes)})

        a = make_apk(self.path("a.apk"), two_dex(["Lcom/example/C;"]))
        b = make_apk(self.path("b.apk"), two_dex(["Lcom/example/C;", "Lcom/example/D;"]))
        unallowed, _, _ = compare_files(a, b)
        self.assertEqual([(d.category, d.subject) for d in unallowed], [("dex.added", "Lcom/example/D;")])

    def test_class_moved_between_dex_files_is_informational(self):
        a = make_apk(self.path("a.apk"), base_entries(**{"classes2.dex": make_dex(["Lcom/example/C;"])}))
        b = make_apk(
            self.path("b.apk"),
            base_entries(**{"classes2.dex": make_dex(["Lcom/example/X;"]),
                            "classes3.dex": make_dex(["Lcom/example/C;"])}),
        )
        unallowed, _, info = compare_files(a, b)
        self.assertEqual(
            sorted((d.category, d.subject) for d in unallowed),
            [("dex.added", "Lcom/example/X;"), ("zip.added", "classes3.dex")],
        )
        self.assertEqual(info["dex.moved"], 1)


class ResourceParserTest(unittest.TestCase):
    SAMPLE = """Package com.example id=7f
  type layout id=01 entryCount=1
    resource 0x7f010000 layout/main
      (file) res/layout/main.xml type=XML
  type string id=03 entryCount=1
    resource 0x7f030000 string/app_name
      (string8) "Example"
      config fr
        (string8) "Exemple"
"""

    def test_parses_ids_and_configs(self):
        parsed = parity.parse_resource_dump(self.SAMPLE)
        self.assertEqual(sorted(parsed), ["layout/main", "string/app_name"])
        self.assertEqual(parsed["layout/main"]["id"], "0x7f010000")
        self.assertEqual(parsed["string/app_name"]["configs"][""], '(string8) "Example"')
        self.assertEqual(parsed["string/app_name"]["configs"]["fr"], '(string8) "Exemple"')

    def test_resource_comparison_counts(self):
        a = {"arsc": parity.parse_resource_dump(self.SAMPLE), "zip": {}, "dex": {}}
        changed = self.SAMPLE.replace('"Example"', '"Sample"').replace("0x7f030000", "0x7f030001")
        b = {"arsc": parity.parse_resource_dump(changed + "    resource 0x7f030002 string/extra\n"
                                                        "      (string8) \"x\"\n"),
             "zip": {}, "dex": {}}
        unallowed, _, _ = parity.compare_apks(a, b)
        self.assertEqual(
            sorted((d.category, d.subject) for d in unallowed),
            [
                ("arsc.added", "string/extra"),
                ("arsc.id.changed", "string/app_name"),
                ("arsc.value.changed", "string/app_name"),
            ],
        )


class AllowListTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp(prefix="parity-allow-")

    def tearDown(self):
        shutil.rmtree(self.dir, ignore_errors=True)

    def write(self, text):
        path = os.path.join(self.dir, "allow.txt")
        with open(path, "w", encoding="utf-8") as f:
            f.write(text)
        return path

    def test_comments_and_blank_lines_are_skipped(self):
        path = self.write("# a comment\n\nzip.added classes*.dex\n")
        self.assertEqual(parity.load_allow_list(path), [("zip.added", "classes*.dex")])

    def test_line_without_subject_is_an_error(self):
        path = self.write("zip.added\n")
        with self.assertRaises(ValueError):
            parity.load_allow_list(path)


class MainExitCodeTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp(prefix="parity-main-")

    def tearDown(self):
        shutil.rmtree(self.dir, ignore_errors=True)

    def run_main(self, argv):
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            code = parity.main(argv)
        return code, out.getvalue()

    def test_same_apk_exits_zero(self):
        a = make_apk(os.path.join(self.dir, "a.apk"), base_entries())
        code, text = self.run_main([a, a, "--no-signing"])
        self.assertEqual(code, 0, text)
        self.assertIn("NOT ALLOWED: 0", text)

    def test_difference_exits_one_and_allow_list_exits_zero(self):
        a = make_apk(os.path.join(self.dir, "a.apk"), base_entries())
        b = make_apk(os.path.join(self.dir, "b.apk"), base_entries(**{"res/layout/main.xml": b"x"}))
        self.assertEqual(self.run_main([a, b, "--no-signing"])[0], 1)

        allow = os.path.join(self.dir, "allow.txt")
        with open(allow, "w", encoding="utf-8") as f:
            f.write("zip.changed res/layout/main.xml\n")
        self.assertEqual(self.run_main([a, b, "--no-signing", "--allow", allow])[0], 0)

    def test_missing_file_is_usage_error(self):
        a = make_apk(os.path.join(self.dir, "a.apk"), base_entries())
        code, _ = self.run_main([a, os.path.join(self.dir, "missing.apk"), "--no-signing"])
        self.assertEqual(code, 2)

    def test_json_output_lists_differences(self):
        import json

        a = make_apk(os.path.join(self.dir, "a.apk"), base_entries())
        b = make_apk(os.path.join(self.dir, "b.apk"), base_entries(**{"assets/data.bin": b"z"}))
        out_json = os.path.join(self.dir, "diff.json")
        self.run_main([a, b, "--no-signing", "--json", out_json])
        with open(out_json, encoding="utf-8") as f:
            data = json.load(f)
        self.assertEqual([d["subject"] for d in data["not_allowed"]], ["assets/data.bin"])


def _aapt2_and_platform():
    build_tools = parity.DEFAULT_BUILD_TOOLS
    aapt2 = os.path.join(build_tools, "aapt2.exe" if os.name == "nt" else "aapt2")
    platforms = os.path.join(os.path.dirname(os.path.dirname(build_tools)), "platforms")
    if not os.path.isfile(aapt2) or not os.path.isdir(platforms):
        return None, None
    jars = sorted(
        (os.path.join(platforms, d, "android.jar") for d in os.listdir(platforms)),
        reverse=True,
    )
    jars = [j for j in jars if os.path.isfile(j)]
    return aapt2, (jars[0] if jars else None)


@unittest.skipUnless(all(_aapt2_and_platform()), "aapt2 and an android.jar are needed")
class AaptIntegrationTest(unittest.TestCase):
    """Builds small resource tables with aapt2 and compares them through the real dump."""

    @classmethod
    def setUpClass(cls):
        cls.aapt2, cls.jar = _aapt2_and_platform()
        cls.dir = tempfile.mkdtemp(prefix="parity-aapt-")

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.dir, ignore_errors=True)

    def build(self, name, label, extra_string=None):
        root = os.path.join(self.dir, name)
        res = os.path.join(root, "res", "values")
        os.makedirs(res, exist_ok=True)
        strings = f'<string name="app_name">{label}</string>'
        if extra_string:
            strings += f'<string name="extra">{extra_string}</string>'
        with open(os.path.join(res, "strings.xml"), "w", encoding="utf-8") as f:
            f.write(f'<?xml version="1.0" encoding="utf-8"?><resources>{strings}</resources>')
        manifest = os.path.join(root, "AndroidManifest.xml")
        with open(manifest, "w", encoding="utf-8") as f:
            f.write('<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
                    'package="com.example.test"/>')
        compiled = os.path.join(root, "res.zip")
        subprocess.run([self.aapt2, "compile", "--dir", os.path.join(root, "res"), "-o", compiled],
                       check=True, capture_output=True)
        apk = os.path.join(root, "out.apk")
        subprocess.run([self.aapt2, "link", "-o", apk, "-I", self.jar, "--manifest", manifest, compiled],
                       check=True, capture_output=True)
        return apk

    def test_dump_parses_real_output(self):
        apk = self.build("base", "Example")
        resources = parity.read_resources(apk, parity.DEFAULT_BUILD_TOOLS)
        self.assertIn("string/app_name", resources)
        self.assertTrue(resources["string/app_name"]["id"].startswith("0x7f"))

    def test_changed_value_and_added_string_are_reported(self):
        a = self.build("a", "Example")
        b = self.build("b", "Sample", extra_string="more")
        ra = parity.read_resources(a, parity.DEFAULT_BUILD_TOOLS)
        rb = parity.read_resources(b, parity.DEFAULT_BUILD_TOOLS)
        unallowed, _, _ = parity.compare_apks(
            {"arsc": ra, "zip": {}, "dex": {}}, {"arsc": rb, "zip": {}, "dex": {}}
        )
        self.assertEqual(
            sorted((d.category, d.subject) for d in unallowed),
            [("arsc.added", "string/extra"), ("arsc.value.changed", "string/app_name")],
        )


if __name__ == "__main__":
    unittest.main()
