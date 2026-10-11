// SPDX-License-Identifier: GPL-3.0-or-later
plugins { kotlin("jvm") }
kotlin { jvmToolchain(21) }
dependencies {
    testImplementation(libs.junit)
    testImplementation("com.google.code.gson:gson:2.13.2")
}
tasks.jar { from("THIRD-PARTY-NOTICES") { into("META-INF") } }
val cache = providers.environmentVariable("SISTRUM_ANALYSIS_ORACLE_DIR")
    .orElse(providers.systemProperty("user.home").map { "$it/.cache/sistrum-analysis-oracle" })
val verifyAnalysisCorpus = tasks.register<Exec>("verifyAnalysisCorpus") {
    workingDir(rootProject.layout.projectDirectory)
    commandLine("python3", "scripts/analysis-oracle/corpus.py", "--verify")
    environment("SISTRUM_ANALYSIS_ORACLE_DIR", cache.get())
}
tasks.withType<Test>().configureEach {
    dependsOn(verifyAnalysisCorpus)
    systemProperty("analysis.cache", cache.get())
    systemProperty("analysis.repo", rootProject.layout.projectDirectory.asFile.absolutePath)
    inputs.file(rootProject.layout.projectDirectory.file("docs/analysis/corpus-manifest.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/analysis/oracle.json"))
    maxHeapSize = "768m"
}
tasks.register<Test>("testMood") {
    dependsOn(tasks.testClasses)
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter { includeTestsMatching("*Mood*") }
}
tasks.register<Test>("testBoundary") {
    dependsOn(tasks.testClasses)
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter { includeTestsMatching("*MetadataBoundaryTest*") }
}
tasks.register<JavaExec>("benchmark") {
    dependsOn(tasks.testClasses, verifyAnalysisCorpus)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("me.misa198.airmedy.analysis.AnalysisBenchmark")
    systemProperty("analysis.cache", cache.get())
    systemProperty("analysis.repo", rootProject.layout.projectDirectory.asFile.absolutePath)
    args(layout.buildDirectory.file("reports/benchmark.tsv").get().asFile.absolutePath)
}
