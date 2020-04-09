plugins {
    `java-library`
}

kotlin {
    experimental.coroutines = org.jetbrains.kotlin.gradle.dsl.Coroutines.ENABLE
}

// TODO(svein): Shadow this, but do it such that we don't get one copy per subproject.
//jar {
//    from { configurations.compile.collect { it.isDirectory() ? it : zipTree(it) } }
//}
