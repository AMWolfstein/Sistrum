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

val alacPackets = providers.gradleProperty("waxflowAlacPackets")
    .orElse(providers.environmentVariable("WAXFLOW_ALAC_PACKETS"))
    .orElse("/tmp/sistrum-waxflow-oracle/alac-packets")

tasks.test {
    inputs.dir(alacPackets).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("waxflow.alacPackets", alacPackets.get())
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


tasks.register<JavaExec>("benchmarkApe") {
    group = "verification"
    description = "Measure APE decode time / audio duration and loop allocations."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("me.misa198.airmedy.codecs.ApeBenchmark")
    systemProperty("waxflow.corpus", corpus.get())
    systemProperty("waxflow.fixtures", fixtures.asFile.absolutePath)
    args(layout.buildDirectory.file("reports/ape-benchmark.tsv").get().asFile.absolutePath)
    maxHeapSize = "512m"
}


tasks.register<JavaExec>("benchmarkAlac") {
    group = "verification"
    description = "Measure ALAC packet decode time / audio duration and loop allocations."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("me.misa198.airmedy.codecs.AlacBenchmark")
    systemProperty("waxflow.corpus", corpus.get())
    systemProperty("waxflow.fixtures", fixtures.asFile.absolutePath)
    systemProperty("waxflow.alacPackets", alacPackets.get())
    args(layout.buildDirectory.file("reports/alac-benchmark.tsv").get().asFile.absolutePath)
    maxHeapSize = "512m"
}


tasks.register<JavaExec>("benchmarkMusepack") {
    group = "verification"
    description = "Measure Musepack float PCM decode time and allocations."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("me.misa198.airmedy.codecs.MusepackBenchmark")
    systemProperty("waxflow.corpus", corpus.get())
    systemProperty("waxflow.fixtures", fixtures.asFile.absolutePath)
    args(layout.buildDirectory.file("reports/musepack-benchmark.tsv").get().asFile.absolutePath)
    maxHeapSize = "512m"
}


tasks.register<JavaExec>("benchmarkAdpcm") {
    group = "verification"
    description = "Measure missing ADPCM variants' decode time and allocations."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("me.misa198.airmedy.codecs.AdpcmBenchmark")
    systemProperty("waxflow.corpus", corpus.get())
    systemProperty("waxflow.fixtures", fixtures.asFile.absolutePath)
    args(layout.buildDirectory.file("reports/adpcm-benchmark.tsv").get().asFile.absolutePath)
    maxHeapSize = "512m"
}

tasks.register<JavaExec>("benchmarkG711") {
    group = "verification"
    description = "Measure G.711 fallback decode time and allocations."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("me.misa198.airmedy.codecs.G711Benchmark")
    systemProperty("waxflow.corpus", corpus.get())
    systemProperty("waxflow.fixtures", fixtures.asFile.absolutePath)
    args(layout.buildDirectory.file("reports/g711-benchmark.tsv").get().asFile.absolutePath)
    maxHeapSize = "512m"
}
