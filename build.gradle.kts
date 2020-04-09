plugins {
    java
    kotlin("jvm") version "1.3.71" apply false
}

allprojects {
    version = "0.1.0"
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

subprojects {
    repositories {
        mavenCentral()
    }
    // We assume all subprojects use Java/Kotlin.
    apply {
        plugin("java")
        plugin("kotlin")
    }

    // And configure them to use the same Kotlin version.
    // Quotes are needed because *this* project does not use Kotlin,
    // and lacks an 'implementation' configuration.
    dependencies {
        "implementation"(kotlin("stdlib-jdk8"))
    }
}

// By default build everything, put it somewhere convenient, and run the tests.
defaultTasks = listOf("bundle", "check")

tasks {
    create<Copy>("bundle") {
        description = "Copies artifacts to the dist directory"
        group = "Build"

        evaluationDependsOnChildren()

        getTasksByName("jar", true).forEach {
            from(it)
        }

        into("dist")
    }

    create<Wrapper>("wrapper") {
        gradleVersion = "4.10.3"
        distributionType = Wrapper.DistributionType.ALL
    }
}
