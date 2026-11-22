"""Lightweight structure checks; not a substitute for Gradle and real-device tests."""
from pathlib import Path
from xml.etree import ElementTree

root = Path(__file__).resolve().parent.parent
manifest = root / "app/src/main/AndroidManifest.xml"
ElementTree.parse(manifest)
for p in ["settings.gradle", "app/build.gradle", ".github/workflows/build-apk.yml",
          "app/src/main/java/com/ollamapocket/app/MainActivity.java",
          "app/src/main/java/com/ollamapocket/app/OllamaHttp.java",
          "app/src/main/java/com/ollamapocket/app/OllamaForegroundService.java"]:
    assert (root / p).is_file(), p
src = (root / "app/src/main/java/com/ollamapocket/app/OllamaHttp.java").read_text()
assert "127.0.0.1:11434" in src
assert '/api/chat' in src and '/api/pull' in src and '/api/tags' not in src  # tags via get()
service = (root / "app/src/main/java/com/ollamapocket/app/OllamaForegroundService.java").read_text()
assert 'libollama.so' in service and 'libllama-server.so' in service
assert '127.0.0.1:11434' in service
print("PASS: project structure, manifest, localhost binding, native binary names")
