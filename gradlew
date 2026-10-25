#!/bin/sh
# Gradle wrapper script for Linux/macOS
# https://gradle.org
APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

set -e
APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
exec "$JAVACMD" $JAVA_OPTS "$DEFAULT_JVM_OPTS" \
  -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain "$@"

