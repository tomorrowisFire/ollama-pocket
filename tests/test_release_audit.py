import io
import pathlib
import struct
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "scripts"))
import release_audit as audit

def elf(align=16384, off=0, addr=0, machine=183, kind=3, count=1):
    ident = b"\x7fELF\x02\x01\x01" + b"\0" * 9
    hdr = struct.pack("<16sHHIQQQIHHHHHH", ident, kind, machine, 1, 0, 64, 0, 0, 64, 56, count, 0, 0, 0)
    ph = struct.pack("<IIQQQQQQ", 1, 5, off, addr, 0, 0, 0, align)
    return hdr + ph

class AuditTests(unittest.TestCase):
    def test_16k(self):
        data = elf()
        self.assertEqual(audit.inspect_elf(io.BytesIO(data), len(data)), ([16384], []))
    def test_4k(self):
        data = elf(4096)
        self.assertTrue(audit.inspect_elf(io.BytesIO(data), len(data))[1])
    def test_congruence(self):
        data = elf(addr=4096)
        self.assertTrue(audit.inspect_elf(io.BytesIO(data), len(data))[1])
    def test_wrong_architecture(self):
        with self.assertRaises(ValueError):
            audit.inspect_elf(io.BytesIO(elf(machine=62)), 120)
    def test_bad_table(self):
        with self.assertRaises(ValueError):
            audit.inspect_elf(io.BytesIO(elf(count=65535)), 120)
    def test_bad_elf(self):
        with self.assertRaises(ValueError):
            audit.inspect_elf(io.BytesIO(b"bad"), 3)
    def test_aab_payload(self):
        with tempfile.TemporaryDirectory() as d:
            p = pathlib.Path(d) / "qa.aab"
            with zipfile.ZipFile(p, "w") as z:
                for name in audit.NATIVE_REQUIRED:
                    z.writestr("base/lib/arm64-v8a/" + name, elf())
            self.assertEqual(audit.inspect_zip(p)["status"], "PASS")
    def test_missing_payload(self):
        with tempfile.TemporaryDirectory() as d:
            p = pathlib.Path(d) / "qa.aab"
            with zipfile.ZipFile(p, "w") as z:
                z.writestr("base/lib/arm64-v8a/libone.so", elf())
            self.assertEqual(audit.inspect_zip(p)["status"], "FAIL")
    def test_compressed_apk(self):
        with tempfile.TemporaryDirectory() as d:
            p = pathlib.Path(d) / "qa.apk"
            with zipfile.ZipFile(p, "w", compression=zipfile.ZIP_DEFLATED) as z:
                z.writestr("lib/arm64-v8a/libone.so", elf())
            self.assertEqual(audit.inspect_zip(p, False)["status"], "PASS")
    def test_unaligned_stored_apk(self):
        with tempfile.TemporaryDirectory() as d:
            p = pathlib.Path(d) / "qa.apk"
            with zipfile.ZipFile(p, "w") as z:
                z.writestr("lib/arm64-v8a/libone.so", elf())
            self.assertEqual(audit.inspect_zip(p, False)["status"], "FAIL")

if __name__ == "__main__":
    unittest.main()
