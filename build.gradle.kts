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

    dependencies {
        // Configure them to use the same Kotlin version.
        // Quotes are needed because *this* project does not use Kotlin,
        // and lacks an 'implementation' configuration.
        "implementation"(kotlin("stdlib-jdk8"))
        compile("org.apache.commons", "commons-math3", "3.6.1")
        // Configure testing.
        testImplementation("org.assertj", "assertj-core", "3.12.2")
        testImplementation("org.junit.jupiter", "junit-jupiter-api", "5.4.2")
        testRuntime("org.junit.jupiter", "junit-jupiter-engine", "5.4.2")
    }

    tasks {
        named<Test>("test") {
            useJUnitPlatform()
        }
    }
}

// By default build everything, put it somewhere convenient, and run the tests.
defaultTasks = listOf("bundle", "test")

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
