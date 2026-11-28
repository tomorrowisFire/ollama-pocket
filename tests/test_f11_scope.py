import importlib.util
import pathlib
import unittest

script = pathlib.Path(__file__).resolve().parents[1] / "scripts" / "test_f11_scope.py"
spec = importlib.util.spec_from_file_location("f11_scope_gate", script)
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)

class ScopeTests(unittest.TestCase):
    def setUp(self):
        self.file = "app/src/main/java/com/ollamapocket/app/CloudApi.java"
        self.protected = "app/src/main/AndroidManifest.xml"
        self.new = "tests/F11IntegratedPolicyTest.java"
        self.old = ("100644", "blob", "a" * 40)
        self.updated = ("100644", "blob", "b" * 40)
        self.before = {self.file: self.old, self.protected: self.old}

    def test_approved_delta(self):
        self.assertEqual(gate.audit(self.before, {**self.before, self.file: self.updated,
                                                  self.new: self.updated}), [])

    def test_protected_modify(self):
        self.assertEqual(len(gate.audit(self.before, {**self.before, self.protected: self.updated})), 1)

    def test_removal(self):
        self.assertEqual(len(gate.audit(self.before, {self.protected: self.old})), 1)

    def test_unknown_addition(self):
        self.assertEqual(len(gate.audit(self.before, {**self.before, "app/native.bin": self.updated})), 1)

    def test_symlink_or_mode_change(self):
        for mode, kind in (("120000", "blob"), ("100755", "blob"), ("160000", "commit")):
            self.assertEqual(len(gate.audit(self.before, {**self.before, self.file: (mode, kind, "b"*40)})), 1)
            self.assertEqual(len(gate.audit(self.before, {**self.before, self.new: (mode, kind, "b"*40)})), 1)

    def test_no_renaming_around_gate(self):
        self.assertEqual(len(gate.audit(self.before, {self.file: self.old, self.new: self.old})), 1)

if __name__ == "__main__":
    unittest.main()
