// Maven Central / Gradle Plugin Portal only - deliberately no google(), so
// this builds where dl.google.com is blocked (see README.md).
pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "ui-preview"
include(":data")
