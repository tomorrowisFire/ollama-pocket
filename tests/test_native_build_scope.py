import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from native_build_scope import CHECKPOINT, DEV_REF, JAVA_PREFIX, changed_paths, decide, safe_path


class NativeBuildScopeTest(unittest.TestCase):
    def test_paths(self):
        safe = [JAVA_PREFIX + x for x in ("MainActivity.java", "VisionImageInput.java", "VisionImagePolicy.java", "WorkbenchStyle.java", "WorkflowPolicy.java")]
        safe += ["app/src/main/res/values-ko/strings.xml", "tests/VisionImagePolicyTest.java", "docs/stage-note.md", "scripts/test_neural_tts_host.sh", ".github/workflows/java-compile.yml"]
        mandatory = [JAVA_PREFIX + x for x in ("NativeNeuralTts.java", "GpuRunner.java", "DistributedRunner.java", "EngineLoadPolicy.java", "OllamaHttp.java", "RpcSocketRegistry.java", "NeuralTtsPlayer.java", "WhisperCapture.java")]
        mandatory += [CHECKPOINT, "app/src/main/cpp/new_backend.cpp", "app/src/main/jniLibs/arm64-v8a/libfoo.so", "app/src/neuralVoice/java/Sherpa.java", "app/src/main/AndroidManifest.xml", "app/src/main/assets/licenses/model.txt", "app/build.gradle", "gradle.properties", "scripts/build_rpc_cpu.sh", "scripts/native_build_scope.py", ".github/workflows/build-coop-apk.yml", "unclassified/new.file", "", "../file", "docs/../app/build.gradle", "tests\\bad", JAVA_PREFIX + "nested/Thing.java"]
        for path in safe:
            with self.subTest(path=path):
                self.assertTrue(safe_path(path))
                self.assertFalse(decide("push", DEV_REF, [path])[0])
        for path in mandatory:
            with self.subTest(path=path):
                self.assertFalse(safe_path(path))
                self.assertTrue(decide("push", DEV_REF, safe + [path])[0])
        self.assertTrue(decide("push", DEV_REF, [])[0])
        self.assertTrue(decide("push", DEV_REF, None)[0])
        self.assertTrue(decide("workflow_dispatch", DEV_REF, safe)[0])
        self.assertTrue(decide("push", "refs/heads/main", safe)[0])
        self.assertTrue(decide("pull_request", DEV_REF, safe)[0])

    def test_exact_git_range_and_rename(self):
        with tempfile.TemporaryDirectory() as directory:
            old = Path.cwd()
            os.chdir(directory)
            try:
                def git(*args):
                    return subprocess.check_output(["git", *args], text=True, stderr=subprocess.DEVNULL).strip()
                git("init", "-q"); git("config", "user.email", "test@example.invalid"); git("config", "user.name", "Fixture")
                Path("docs").mkdir(); Path("docs/note.md").write_text("first\n")
                git("add", "."); git("commit", "-qm", "base"); before = git("rev-parse", "HEAD")
                Path("docs/note.md").write_text("second\n"); git("commit", "-qam", "safe"); safe = git("rev-parse", "HEAD")
                self.assertEqual(changed_paths(before, safe), ["docs/note.md"])
                self.assertFalse(decide("push", DEV_REF, changed_paths(before, safe))[0])
                git("mv", "docs/note.md", "native.cpp"); git("commit", "-qm", "rename"); head = git("rev-parse", "HEAD")
                self.assertEqual(set(changed_paths(safe, head)), {"docs/note.md", "native.cpp"})
                self.assertTrue(decide("push", DEV_REF, changed_paths(before, head))[0])
                for a, b in [("0" * 40, head), (before, before), ("invalid", head), (head, before)]:
                    with self.subTest(a=a, b=b), self.assertRaises((ValueError, subprocess.SubprocessError)):
                        changed_paths(a, b)
            finally:
                os.chdir(old)


if __name__ == "__main__":
    unittest.main()
