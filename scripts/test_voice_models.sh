#!/usr/bin/env bash
set -euo pipefail

# Host-only integrity/lifecycle fixtures. Optional: pinned Supertonic archive, --melo or --vision verified fixture directory.
voice_repo_root=$(cd "$(dirname "$0")/.." && pwd)
voice_dependency_dir=${ONHAND_VOICE_TEST_DEPS:-${TMPDIR:-/tmp}/onhand-voice-test-deps}
voice_test_dir=$(mktemp -d "${TMPDIR:-/tmp}/onhand-voice-model-tests.XXXXXX")
trap 'rm -rf "$voice_test_dir"' EXIT
mkdir -p "$voice_dependency_dir"

voice_fetch_jar() {
  bash "$voice_repo_root/scripts/fetch_pinned_test_jar.sh" "$1" "$2" "$voice_dependency_dir/${1##*/}"
}
voice_fetch_jar org/apache/commons/commons-compress/1.28.0/commons-compress-1.28.0.jar e1522945218456f3649a39bc4afd70ce4bd466221519dba7d378f2141a4642ca
voice_fetch_jar commons-io/commons-io/2.20.0/commons-io-2.20.0.jar df90bba0fe3cb586b7f164e78fe8f8f4da3f2dd5c27fa645f888100ccc25dd72
voice_fetch_jar org/apache/commons/commons-lang3/3.18.0/commons-lang3-3.18.0.jar 4eeeae8d20c078abb64b015ec158add383ac581571cddc45c68f0c9ae0230720

cd "$voice_repo_root"
voice_classpath="$voice_dependency_dir/commons-compress-1.28.0.jar:$voice_dependency_dir/commons-io-2.20.0.jar:$voice_dependency_dir/commons-lang3-3.18.0.jar"
javac --release 17 -cp "$voice_classpath" -d "$voice_test_dir" \
  app/src/main/java/com/ollamapocket/app/VoiceModelCatalog.java \
  app/src/main/java/com/ollamapocket/app/VoiceModelStore.java \
  app/src/main/java/com/ollamapocket/app/VoiceModelTree.java \
  app/src/main/java/com/ollamapocket/app/VisionImagePolicy.java \
  app/src/main/java/com/ollamapocket/app/VisionModelProfile.java \
  app/src/main/java/com/ollamapocket/app/VisionModelStore.java \
  tests/VoiceModelStoreTest.java tests/VoiceModelTreeTest.java tests/VisionModelStoreTest.java
java -cp "$voice_test_dir:$voice_classpath" com.ollamapocket.app.VoiceModelTreeTest
if [[ "$#" -eq 2 && "$1" == "--vision" ]]; then
  java -cp "$voice_test_dir:$voice_classpath" com.ollamapocket.app.VisionModelStoreTest "$2"
else
  java -cp "$voice_test_dir:$voice_classpath" com.ollamapocket.app.VoiceModelStoreTest "$@"
  java -cp "$voice_test_dir:$voice_classpath" com.ollamapocket.app.VisionModelStoreTest
fi
