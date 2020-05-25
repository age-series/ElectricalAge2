rootProject.name = "eln2"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven(url = "https://dl.bintray.com/kotlin/kotlin-eap")
    }
}

include("core")
include("apps")
include("proto")
include("integration-mc1-15")
