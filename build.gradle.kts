group = "io.github.lostatc"
version = "0.1"

plugins {
    kotlin("jvm") version "1.3.0"
}

val test by tasks.getting(Test::class) {
    useJUnitPlatform { }
}

dependencies {
    compile(kotlin("stdlib"))
    testCompile(kotlin("reflect"))
    testCompile(kotlin("stdlib-jdk7"))
    testCompile(group = "io.kotlintest", name = "kotlintest-runner-junit5", version = "3.1.10")
    testCompile(group = "com.google.jimfs", name = "jimfs", version = "1.1")
}

repositories {
    jcenter()
}
