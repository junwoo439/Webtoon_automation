#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p build/core-test
java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d build/core-test \
  app/src/main/java/app/toonpace/PaceModel.java \
  app/src/main/java/app/toonpace/ButtonPolicy.java \
  app/src/main/java/app/toonpace/NextButtonDetector.java \
  app/src/main/java/app/toonpace/InteractionGuard.java \
  app/src/main/java/app/toonpace/ScrollEndTracker.java \
  tests/CoreTest.java tests/InteractionGuardTest.java tests/ScrollEndTest.java tests/NextButtonTest.java
java -cp build/core-test app.toonpace.CoreTest
java -cp build/core-test app.toonpace.InteractionGuardTest
java -cp build/core-test app.toonpace.ScrollEndTest
java -cp build/core-test app.toonpace.NextButtonTest
