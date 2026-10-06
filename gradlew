#!/bin/sh
#
# Minimal Gradle wrapper launcher (macOS/Linux).
#

DIR="$(cd "$(dirname "$0")" && pwd)"
APP_HOME="$DIR"

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

JAVA_CMD="java"
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
fi

exec "$JAVA_CMD" -Dorg.gradle.appname="$(basename "$0")" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
