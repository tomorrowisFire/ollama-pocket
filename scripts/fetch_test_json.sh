#!/usr/bin/env bash
# JVM-only org.json fixture dependency; no app runtime dependency is added.
set -euo pipefail
test "$#" -eq 1
dest="$1"
mkdir -p "$(dirname "$dest")"
temp=$(mktemp -d)
trap 'rm -rf "$temp"' EXIT
artifact="org/json/json/20240303/json-20240303.jar"
fetch() {
  curl --fail --silent --show-error --location --proto '=https' --proto-redir '=https' \
       --connect-timeout 10 --max-time 25 --retry 2 --retry-delay 2 --retry-max-time 40 \
       "$1" -o "$2"
}
# Published checksums protect against damaged downloads, not repository compromise.
for root in https://repo.maven.apache.org/maven2 https://maven-central.storage-download.googleapis.com/maven2 https://repo1.maven.org/maven2; do
  rm -f "$temp/json.jar" "$temp/checksum"
  if ! fetch "$root/$artifact" "$temp/json.jar"; then continue; fi
  verified=0
  if fetch "$root/$artifact.sha256" "$temp/checksum"; then
    expected=$(awk 'NR==1{print tolower($1)}' "$temp/checksum")
    if [[ "$expected" =~ ^[0-9a-f]{64}$ ]] && [[ "$(sha256sum "$temp/json.jar" | cut -d' ' -f1)" == "$expected" ]]; then verified=1; fi
  elif fetch "$root/$artifact.sha1" "$temp/checksum"; then
    expected=$(awk 'NR==1{print tolower($1)}' "$temp/checksum")
    if [[ "$expected" =~ ^[0-9a-f]{40}$ ]] && [[ "$(sha1sum "$temp/json.jar" | cut -d' ' -f1)" == "$expected" ]]; then verified=1; fi
  fi
  if [[ "$verified" == 1 ]] && jar tf "$temp/json.jar" > "$temp/entries" && grep -qx 'org/json/JSONObject.class' "$temp/entries"; then
    cp "$temp/json.jar" "$dest"
    echo "Verified versioned JVM fixture dependency from $root"
    sha256sum "$dest"
    exit 0
  fi
done
echo "::error::Could not retrieve and validate the pinned org.json test fixture"
exit 1
