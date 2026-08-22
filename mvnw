#!/bin/sh
set -eu
WRAPPER_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
MAVEN_VERSION="3.9.11"
MAVEN_HOME="$WRAPPER_DIR/.tools/apache-maven-$MAVEN_VERSION"
ARCHIVE="$WRAPPER_DIR/.tools/apache-maven-$MAVEN_VERSION-bin.tar.gz"
if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
  mkdir -p "$WRAPPER_DIR/.tools"
  if [ ! -f "$ARCHIVE" ]; then
    if command -v curl >/dev/null 2>&1; then
      curl -fsSL "https://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz" -o "$ARCHIVE"
    else
      wget -q "https://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz" -O "$ARCHIVE"
    fi
  fi
  tar -xzf "$ARCHIVE" -C "$WRAPPER_DIR/.tools"
fi
exec "$MAVEN_HOME/bin/mvn" "$@"
