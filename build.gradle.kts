import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
	java
	kotlin("jvm") version "1.3.71" apply false
	jacoco
}

allprojects {
	version = "0.1.0"
	group = "net.electricalage"

	buildscript {
		repositories {
			jcenter()
			mavenCentral()
		}
	}

	repositories {
		jcenter()
		mavenCentral()
	}
}

subprojects {
	// We assume all subprojects use Java/Kotlin.
	apply {
		plugin("java")
		plugin("kotlin")
		plugin("jacoco")
	}

	java {
		sourceCompatibility = JavaVersion.VERSION_1_8
		targetCompatibility = JavaVersion.VERSION_1_8
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

			// *Always* run tests.
			// Ideally we'd cache the test output and print that instead, but this will do for now.
			outputs.upToDateWhen { false }

			// Print pass/fail for all tests to console, and exceptions if there are any.
			testLogging {
				events = setOf(TestLogEvent.FAILED, TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.STANDARD_ERROR)
				exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
				showExceptions = true
				showCauses = true
				showStackTraces = true

				// At log-level INFO or DEBUG, print everything.
				debug {
					events = TestLogEvent.values().toSet()
				}
				info {
					events = debug.events
				}
			}

			// Print a nice summary afterwards.
			afterSuite(KotlinClosure2<TestDescriptor, TestResult, Unit>({ desc, result ->
				if (desc.parent == null) { // will match the outermost suite
					val output = "Results: ${result.resultType} (${result.testCount} tests, ${result.successfulTestCount} passed, ${result.failedTestCount} failed, ${result.skippedTestCount} skipped)"
					val startItem = "|  "
					val endItem = "  |"
					val repeatLength = startItem.length + output.length + endItem.length
					println('\n' + "- ".repeat(repeatLength) + '\n' + startItem + output + endItem + '\n' + "-".repeat(repeatLength))
				}
			}))
		}

		jacocoTestReport {
			reports {
				xml.isEnabled = true
				csv.isEnabled = false
			}
		}
	}
}

// By default build everything, put it somewhere convenient, and run the tests.
defaultTasks = mutableListOf("bundle", "test")

tasks {
	create<Copy>("bundle") {
		description = "Copies artifacts to the dist directory"
		group = "Build"

		evaluationDependsOnChildren()

		getTasksByName("jar", true).forEach {
			// Ignore the root jar task, it's useless and conflicting.
			if (it.path != ":jar") {
				from(it)
			}
		}

		into("dist")
	}

	named<Wrapper>("wrapper") {
		gradleVersion = "5.6.4"
		distributionType = Wrapper.DistributionType.ALL
	}
}
