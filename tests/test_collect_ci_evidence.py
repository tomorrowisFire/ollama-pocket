import pathlib
import sys
import unittest

sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]/"scripts"))
import collect_ci_evidence as c

def job(name,steps):
    return {"name":name,"status":"completed","conclusion":"success",
            "steps":[{"name":s,"status":"completed","conclusion":"success"} for s in steps]}

def artifact(name):
    return {"name":name,"id":1,"expired":False,"size_in_bytes":123,"digest":"sha256:"+"a"*64}

def receipt(jobs,artifacts=()):
    return {"run":{"status":"completed","conclusion":"success"},"jobs":jobs,"artifacts":list(artifacts)}

class CiReceiptTests(unittest.TestCase):
    def setUp(self):
        self.commit="a"*40
        self.java=receipt([job("compile",c.JAVA_STEPS)])
        self.native=receipt([job("secure-relay-unit-test",["Test AES-GCM loopback tunnel"]),
                             job("cooperative-apk",[*c.NATIVE_STEPS,c.BUNDLE_STEP,c.AUDIT_STEP])],
                            [artifact("AI-in-Pocket-Cooperative-RPC-APK"),artifact("OnHand-QA-UNSIGNED-NOT-FOR-PLAY"),artifact("OnHand-Release-Compatibility-Audit")])

    def test_exact_scope(self):
        run={"id":4,"head_sha":self.commit,"head_branch":c.BRANCH,"path":c.JAVA_PATH,"repository":{"full_name":c.REPO},"run_attempt":1}
        self.assertTrue(c.valid_run(run,4,self.commit,c.JAVA_PATH))
        self.assertFalse(c.valid_run(run,4,"b"*40,c.JAVA_PATH))
        self.assertFalse(c.valid_run(run,5,self.commit,c.JAVA_PATH))
        self.assertFalse(c.valid_run(run,4,self.commit,c.NATIVE_PATH))

    def test_complete(self):
        result=c.ci_status(self.java,self.native)
        self.assertEqual(result["java_regression"],"PASS")
        self.assertEqual(result["native_apk"],"PASS")
        self.assertEqual(result["aab_build"],"PASS")
        self.assertEqual(result["native_16k_audit"],"CI_PASS_REVIEW_REQUIRED")

    def test_pending(self):
        self.native["run"]={"status":"in_progress","conclusion":None}
        self.assertEqual(c.ci_status(self.java,self.native)["native_apk"],"PENDING")

    def test_failed_run(self):
        self.native["run"]["conclusion"]="failure"
        self.assertEqual(c.ci_status(self.java,self.native)["native_apk"],"FAIL")

    def test_missing_step(self):
        self.java["jobs"][0]["steps"].pop()
        self.assertEqual(c.ci_status(self.java,None)["java_regression"],"BLOCKED")

    def test_expired_artifact(self):
        self.native["artifacts"][1]["expired"]=True
        self.assertEqual(c.ci_status(self.java,self.native)["aab_build"],"BLOCKED")

    def test_duplicate_job(self):
        self.java["jobs"].append(self.java["jobs"][0])
        self.assertEqual(c.ci_status(self.java,None)["java_regression"],"BLOCKED")

    def test_duplicate_artifact(self):
        self.native["artifacts"].append(self.native["artifacts"][0])
        self.assertEqual(c.ci_status(self.java,self.native)["native_apk"],"BLOCKED")

    def test_no_device_inference(self):
        statuses=c.ci_status(self.java,self.native)
        m=c.new_manifest(self.commit,statuses,{"java":{"path":"java.json","sha256":"a"*64}})
        self.assertEqual(m["gates"]["signed_update"]["status"],"NOT_RUN")
        self.assertEqual(m["gates"]["workflow_10_steps"]["status"],"NOT_RUN")
        self.assertTrue(all(x["status"]=="NOT_RUN" for x in m["reviews"].values()))

    def test_partial_response(self):
        with self.assertRaises(ValueError):
            c.complete_rows({"total_count":2,"jobs":[{}]},"jobs")

if __name__=="__main__":
    unittest.main()
