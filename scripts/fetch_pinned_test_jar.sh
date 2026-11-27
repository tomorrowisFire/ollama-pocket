#!/usr/bin/env bash
# Host fixture jars only; never used for app model downloads. Every cache hit and
# mirror response must match the repository-pinned SHA-256 before publication.
set -euo pipefail
test "$#" -eq 3
relative=$1 expected=$2 dest=$3
[[ "$relative" =~ ^[a-zA-Z0-9._-]+(/[a-zA-Z0-9._-]+)+[.]jar$ ]]
[[ "$relative" != *..* && "$expected" =~ ^[0-9a-f]{64}$ ]]
mkdir -p "$(dirname "$dest")"
valid() {
  [[ -f "$1" && ! -L "$1" ]] && printf '%s  %s\n' "$expected" "$1" | sha256sum --check --status
}
if valid "$dest"; then exit 0; fi
temp=$(mktemp -d "$(dirname "$dest")/.pinned-jar.XXXXXX")
trap 'rm -rf "$temp"' EXIT
for root in https://repo.maven.apache.org/maven2 https://maven-central.storage-download.googleapis.com/maven2 https://repo1.maven.org/maven2; do
  rm -f "$temp/artifact.jar"
  if curl --fail --silent --show-error --location --proto '=https' --proto-redir '=https' \
      --connect-timeout 10 --max-time 35 --max-filesize 33554432 --retry 1 --retry-delay 2 --retry-max-time 45 \
      "$root/$relative" -o "$temp/artifact.jar" && valid "$temp/artifact.jar"; then
    mv -fT "$temp/artifact.jar" "$dest"
    valid "$dest"
    echo "Verified pinned fixture: ${relative##*/} ($root)"
    exit 0
  fi
done
echo "::error::Pinned fixture unavailable or checksum mismatch: ${relative##*/}" >&2
exit 1
