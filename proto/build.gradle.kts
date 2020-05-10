import com.google.protobuf.gradle.proto
import com.google.protobuf.gradle.protobuf
import com.google.protobuf.gradle.protoc

plugins {
	`java-library`
	id("com.google.protobuf") version "0.8.12"
}

dependencies {
	module("com.google.protobuf:protobuf-gradle-plugin:0.8.12")
}

sourceSets {
	main {
		proto {
			srcDir("src")
		}
	}
}

protobuf {
	protoc {
		// Download from repositories
		artifact = "com.google.protobuf:protoc:3.11.4"
	}
}
