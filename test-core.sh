#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p build/core-test
java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d build/core-test \
  app/src/main/java/app/toonpace/PaceModel.java \
  app/src/main/java/app/toonpace/ButtonPolicy.java \
  app/src/main/java/app/toonpace/InteractionGuard.java \
  tests/CoreTest.java tests/InteractionGuardTest.java
java -cp build/core-test app.toonpace.CoreTest
java -cp build/core-test app.toonpace.InteractionGuardTest
