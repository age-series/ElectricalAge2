plugins {
    `java-library`
}

sourceSets["main"].withConvention(org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet::class) {
    kotlin.srcDir("src")
}

// TODO(svein): Shadow this, but do it such that we don't get one copy per subproject.
//jar {
//    from { configurations.compile.collect { it.isDirectory() ? it : zipTree(it) } }
//}
