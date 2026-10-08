plugins {
    kotlin("jvm")
}

kotlin { jvmToolchain(21) }

dependencies { testImplementation(libs.junit) }

tasks.jar {
    from("THIRD-PARTY-NOTICES") { into("META-INF") }
}

val corpus = providers.gradleProperty("waxflowCorpus")
    .orElse(providers.environmentVariable("WAXFLOW_CORPUS"))
    .orElse("/tmp/sistrum-waxflow-oracle/corpus")
val fixtures = rootProject.layout.projectDirectory.file(
    "androidApp/src/test/resources/waxflow/oracle-corpus-fixtures.tsv"
)

tasks.test {
    inputs.file(fixtures).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(corpus).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("waxflow.corpus", corpus.get())
    systemProperty("waxflow.fixtures", fixtures.asFile.absolutePath)
    maxHeapSize = "512m"
    testLogging { events("passed", "failed", "skipped") }
}

tasks.register<JavaExec>("benchmark") {
    group = "verification"
    description = "Measure WavPack decode time / audio duration on this JVM (lower is faster)."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("me.misa198.airmedy.codecs.WavPackBenchmarkKt")
    systemProperty("waxflow.corpus", corpus.get())
    systemProperty("waxflow.fixtures", fixtures.asFile.absolutePath)
    args(layout.buildDirectory.file("reports/wavpack-benchmark.tsv").get().asFile.absolutePath)
    maxHeapSize = "512m"
}
