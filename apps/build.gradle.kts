plugins {
	application
}

dependencies {
	compile(project(":core"))
}

application.mainClassName = "org.eln2.apps.compute.AsmComputerUiKt"

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>() {
	manifest {
		attributes["Main-Class"] = application.mainClassName
	}
}
