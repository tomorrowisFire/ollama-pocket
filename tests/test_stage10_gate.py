import copy
import hashlib
import pathlib
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "scripts"))
import stage10_gate as gate

class GateTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = pathlib.Path(self.tmp.name)
        payload = b"synthetic unit fixture; NOT real evidence"
        (self.root / "fixture.txt").write_bytes(payload)
        self.commit = "a" * 40
        self.ref = [{"path":"fixture.txt","sha256":hashlib.sha256(payload).hexdigest()}]
        self.manifest = {
            "schema":1,"candidate_commit":self.commit,
            "gates":{k:{"status":"PASS","commit":self.commit,"evidence":self.ref} for k in gate.GATES},
            "reviews":{k:{"status":"REVIEWED","commit":self.commit,"evidence":self.ref,"scores":dict(gate.DIMENSIONS)} for k in gate.REVIEWERS},
            "defects":{"status":"REVIEWED","commit":self.commit,"evidence":self.ref,"unresolved":[]}
        }

    def evaluate(self):
        return gate.evaluate(self.manifest, self.root)

    def test_complete_fixture_only_manual(self):
        self.assertEqual(self.evaluate()["status"],"READY_FOR_MANUAL_APPROVAL")
    def test_missing_device_gate(self):
        self.manifest["gates"]["api36_16k_device"]["status"]="NOT_RUN"
        self.assertEqual(self.evaluate()["status"],"BLOCKED")
    def test_one_reviewer_below_90(self):
        self.manifest["reviews"]["D"]["scores"]["task"]=19
        self.assertEqual(self.evaluate()["mean"],98.9)
        self.assertEqual(self.evaluate()["status"],"BLOCKED")
    def test_missing_reviewer(self):
        del self.manifest["reviews"]["J"]
        self.assertIsNone(self.evaluate()["mean"])
    def test_high_defect(self):
        self.manifest["defects"]["unresolved"]=[{"id":"P1-1","severity":"P1"}]
        self.assertEqual(self.evaluate()["status"],"BLOCKED")
    def test_modified_evidence(self):
        (self.root/"fixture.txt").write_text("modified")
        self.assertEqual(self.evaluate()["status"],"BLOCKED")
    def test_commit_mismatch(self):
        self.manifest["gates"]["native_apk"]["commit"]="b"*40
        self.assertEqual(self.evaluate()["status"],"BLOCKED")
    def test_path_escape(self):
        self.assertFalse(gate.evidence_valid([{"path":"../file","sha256":"a"*64}], self.root))
    def test_missing_evidence(self):
        self.manifest["gates"]["java_regression"]["evidence"]=[]
        self.assertEqual(self.evaluate()["status"],"BLOCKED")
    def test_invalid_rubric(self):
        self.manifest["reviews"]["A"]["scores"]["task"]=31
        self.assertEqual(self.evaluate()["status"],"BLOCKED")
    def test_boolean_rubric(self):
        self.manifest["reviews"]["A"]["scores"]["privacy"]=True
        self.assertEqual(self.evaluate()["status"],"BLOCKED")
    def test_empty_template(self):
        self.assertEqual(gate.evaluate({"schema":1},self.root)["status"],"BLOCKED")

if __name__ == "__main__":
    unittest.main()
