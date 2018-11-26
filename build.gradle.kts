import org.jetbrains.dokka.gradle.DokkaTask

group = "io.github.lostatc"
version = "0.1"

repositories {
    jcenter()
}

plugins {
    kotlin("jvm") version "1.3.0"
    id("org.jetbrains.dokka") version "0.9.17"
}

dependencies {
    compile(kotlin("stdlib"))
    testCompile(kotlin("reflect"))
    testCompile(kotlin("stdlib-jdk7"))
    testCompile(group = "io.kotlintest", name = "kotlintest-runner-junit5", version = "3.1.10")
    testCompile(group = "com.google.jimfs", name = "jimfs", version = "1.1")
}

val test by tasks.getting(Test::class) {
    useJUnitPlatform { }
}

val dokka by tasks.getting(DokkaTask::class) {
    outputFormat = "html"
    outputDirectory = "$buildDir/dokka"
}
