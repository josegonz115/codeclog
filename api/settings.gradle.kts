plugins {
    // Resolves and downloads the JDK named by the toolchain below, so a contributor whose
    // installed JDK is not 25 still builds against 25 rather than silently against theirs.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "codeclog-api"
