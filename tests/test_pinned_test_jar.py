"""No network: exercise mirror failover and immutable checksum admission."""
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
SCRIPT=ROOT/"scripts/fetch_pinned_test_jar.sh"
PAYLOAD=b"fixture bytes"
SHA=hashlib.sha256(PAYLOAD).hexdigest()

class PinnedJarTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name)
        self.dest=self.root/"cache"/"fixture.jar"
        self.log=self.root/"calls"
        curl=self.root/"curl"
        curl.write_text("""#!/bin/bash
set -eu
url=""
out=""
while (($#)); do
  case "$1" in
    https://*) url=$1; shift;;
    -o) out=$2; shift 2;;
    *) shift;;
  esac
done
echo "$url" >> "$MOCK_LOG"
case "$MOCK_MODE:$url" in
  all_fail:*) exit 22;;
  failover:*repo.maven.apache.org*) exit 22;;
  corrupt:*repo.maven.apache.org*) printf 'invalid' > "$out";;
  *) printf 'fixture bytes' > "$out";;
esac
""")
        curl.chmod(0o755)
        self.env=dict(os.environ,PATH=str(self.root)+os.pathsep+os.environ["PATH"],MOCK_LOG=str(self.log))
    def run_fetch(self,mode,sha=SHA,relative="org/example/fixture.jar"):
        return subprocess.run(["bash",str(SCRIPT),relative,sha,str(self.dest)],env=dict(self.env,MOCK_MODE=mode),
            capture_output=True,text=True)
    def calls(self):
        return self.log.read_text().splitlines() if self.log.exists() else []
    def test_http_failure_falls_back(self):
        self.assertEqual(self.run_fetch("failover").returncode,0)
        self.assertEqual(self.dest.read_bytes(),PAYLOAD)
        self.assertEqual(len(self.calls()),2)
        self.assertIn("storage-download.googleapis.com",self.calls()[1])
    def test_wrong_hash_falls_back(self):
        self.assertEqual(self.run_fetch("corrupt").returncode,0)
        self.assertEqual(len(self.calls()),2)
        self.assertEqual(self.dest.read_bytes(),PAYLOAD)
    def test_valid_cache_avoids_network(self):
        self.dest.parent.mkdir();self.dest.write_bytes(PAYLOAD)
        self.assertEqual(self.run_fetch("all_fail").returncode,0)
        self.assertEqual(self.calls(),[])
    def test_corrupt_cache_not_accepted(self):
        self.dest.parent.mkdir();self.dest.write_bytes(b"bad")
        self.assertNotEqual(self.run_fetch("all_fail").returncode,0)
        self.assertEqual(len(self.calls()),3)
        self.assertEqual(self.dest.read_bytes(),b"bad")
        self.assertEqual(list(self.dest.parent.glob(".pinned-jar.*")),[])
    def test_all_digests_wrong(self):
        self.assertNotEqual(self.run_fetch("valid",sha="0"*64).returncode,0)
        self.assertEqual(len(self.calls()),3)
        self.assertFalse(self.dest.exists())
    def test_invalid_path(self):
        self.assertNotEqual(self.run_fetch("valid",relative="../fixture.jar").returncode,0)
        self.assertEqual(self.calls(),[])
if __name__=="__main__":
    unittest.main()
