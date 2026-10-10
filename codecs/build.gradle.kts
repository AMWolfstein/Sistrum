plugins {
    kotlin("jvm")
}

kotlin { jvmToolchain(21) }

dependencies { testImplementation(libs.junit) }

tasks.jar {
    from("THIRD-PARTY-NOTICES") { into("META-INF") }
}

val oracleDir = providers.environmentVariable("SISTRUM_ORACLE_DIR")
    .orElse(providers.systemProperty("user.home").map { "$it/.cache/sistrum-waxflow-oracle" })

val corpus = providers.gradleProperty("waxflowCorpus")
    .orElse(providers.environmentVariable("WAXFLOW_CORPUS"))
    .orElse(oracleDir.map { "$it/corpus" })
val fixtures = rootProject.layout.projectDirectory.file(
    "androidApp/src/test/resources/waxflow/oracle-corpus-fixtures.tsv"
)

val alacPackets = providers.gradleProperty("waxflowAlacPackets")
    .orElse(providers.environmentVariable("WAXFLOW_ALAC_PACKETS"))
    .orElse(oracleDir.map { "$it/alac-packets" })

val dsdCorpus = providers.gradleProperty("dsdCorpus").orElse(oracleDir.map { "$it/dsd/corpus" })
val dsdFixtures = rootProject.layout.projectDirectory.file("androidApp/src/test/resources/dsd/oracle-fixtures.tsv")
val verifyDsdCorpus = tasks.register<Exec>("verifyDsdCorpus") {
    group = "verification"
    description = "Verify synthetic DSD corpus hashes before JVM tests."
    workingDir(rootProject.layout.projectDirectory)
    commandLine("python3", "scripts/dsd-oracle.py", "--verify", dsdCorpus.get())
}

// Always run before Gradle validates Test input directories, even for up-to-date tests.
val verifyOracleCorpus by tasks.registering(Exec::class) {
    group = "verification"
    description = "Verify the external corpus and ALAC dumps against the committed manifest."
    workingDir(rootProject.layout.projectDirectory)
    commandLine("python3", "scripts/waxflow-corpus.py", "--verify", corpus.get(), alacPackets.get())
}

rootProject.allprojects {
    tasks.withType<Test>().configureEach {
        if (name != "testOwnedWavPack") dependsOn(verifyOracleCorpus, verifyDsdCorpus)
        inputs.file(rootProject.layout.projectDirectory.file("docs/waxflow/corpus-manifest.tsv"))
        inputs.file(rootProject.layout.projectDirectory.file("docs/waxflow/alac-packets-manifest.tsv"))
        inputs.file(rootProject.layout.projectDirectory.file("docs/waxflow/dsd-corpus-manifest.tsv"))
    }
}
tasks.withType<JavaExec>().configureEach { dependsOn(verifyOracleCorpus, verifyDsdCorpus) }

tasks.test {
    inputs.dir(dsdCorpus).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(dsdFixtures).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("dsd.corpus", dsdCorpus.get())
    systemProperty("dsd.fixtures", dsdFixtures.asFile.absolutePath)
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
    systemProperty("wavpack.benchmarkFeature", providers.gradleProperty("wavpackBenchmarkFeature").orElse("all").get())
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


tasks.register<JavaExec>("benchmarkWma") {
    group = "verification"
    description = "Measure ASF/WMA v1/v2 float PCM decode time and allocations."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("me.misa198.airmedy.codecs.WmaBenchmark")
    systemProperty("waxflow.corpus", corpus.get())
    systemProperty("waxflow.fixtures", fixtures.asFile.absolutePath)
    args(layout.buildDirectory.file("reports/wma-benchmark.tsv").get().asFile.absolutePath)
    maxHeapSize = "512m"
}

tasks.register<JavaExec>("benchmarkWmaLossless") {
    group = "verification"
    description = "Measure ASF/WMA Lossless integer PCM decode time and allocations."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("me.misa198.airmedy.codecs.WmaLosslessBenchmark")
    systemProperty("waxflow.corpus", corpus.get())
    systemProperty("waxflow.fixtures", fixtures.asFile.absolutePath)
    args(layout.buildDirectory.file("reports/wmalossless-benchmark.tsv").get().asFile.absolutePath)
    maxHeapSize = "512m"
}


tasks.register<JavaExec>("benchmarkWmaPro") {
    group = "verification"
    description = "Measure ASF/WMA Pro float PCM decode time and allocations."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("me.misa198.airmedy.codecs.WmaProBenchmark")
    systemProperty("waxflow.corpus", corpus.get())
    systemProperty("waxflow.fixtures", fixtures.asFile.absolutePath)
    args(layout.buildDirectory.file("reports/wmapro-benchmark.tsv").get().asFile.absolutePath)
    maxHeapSize = "512m"
}

tasks.register<JavaExec>("benchmarkWmaVoice") {
    group = "verification"
    description = "Measure ASF/WMA Voice float PCM decode time and allocations."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("me.misa198.airmedy.codecs.WmaVoiceBenchmark")
    systemProperty("waxflow.corpus", corpus.get())
    systemProperty("waxflow.fixtures", fixtures.asFile.absolutePath)
    args(layout.buildDirectory.file("reports/wmavoice-benchmark.tsv").get().asFile.absolutePath)
    maxHeapSize = "512m"
}

tasks.register<JavaExec>("benchmarkAiff") {
    group = "verification"
    description = "Measure ASF/AIFF integer/float PCM decode time and allocations."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("me.misa198.airmedy.codecs.AiffBenchmark")
    systemProperty("waxflow.corpus", corpus.get())
    systemProperty("waxflow.fixtures", fixtures.asFile.absolutePath)
    args(layout.buildDirectory.file("reports/aiff-benchmark.tsv").get().asFile.absolutePath)
    maxHeapSize = "512m"
}

tasks.register<JavaExec>("benchmarkDsd") {
    group = "verification"
    description = "Measure DSD PCM decimation and codec-only DoP packing time and allocations."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("me.misa198.airmedy.codecs.DsdBenchmark")
    systemProperty("dsd.corpus", dsdCorpus.get())
    systemProperty("dsd.fixtures", dsdFixtures.asFile.absolutePath)
    args(layout.buildDirectory.file("reports/dsd-benchmark.tsv").get().asFile.absolutePath)
    maxHeapSize = "512m"
}

val verifyOwnedWavPack = tasks.register<Exec>("verifyOwnedWavPack") {
    workingDir(rootProject.layout.projectDirectory)
    commandLine("python3", "scripts/wavpack-oracle.py", "--verify")
}
tasks.test { dependsOn(verifyOwnedWavPack) }
tasks.register<Test>("testOwnedWavPack") {
    group = "verification"
    description = "Run owned WavPack parity and seek vectors without external audio."
    dependsOn(tasks.testClasses, verifyOwnedWavPack)
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter { includeTestsMatching("*LibWavPackOracleTest"); includeTestsMatching("*WavPackHybridContractsTest") }
    systemProperty("wavpack.ownedOnly", "true")
    maxHeapSize = "512m"
}

tasks.named("benchmark") { dependsOn(verifyOwnedWavPack) }
