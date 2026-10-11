// SPDX-License-Identifier: GPL-3.0-or-later
// Sistrum WaxLabel read port; Copyright (c) 2026 Cole Springer (MIT).
plugins { kotlin("jvm") }
kotlin { jvmToolchain(21) }
dependencies {
    implementation(project(":codecs")) // RandomAccessSource only; no decoder calls.
    testImplementation(libs.junit)
    testImplementation("com.google.code.gson:gson:2.13.2")
}
tasks.jar { from("THIRD-PARTY-NOTICES") { into("META-INF") } }
val oracleRoot = providers.environmentVariable("SISTRUM_WAXLABEL_ORACLE_DIR")
    .orElse(providers.systemProperty("user.home").map { "$it/.cache/sistrum-waxlabel-oracle" })
val verifyWaxLabelCorpus = tasks.register<Exec>("verifyWaxLabelCorpus") {
    workingDir(rootProject.layout.projectDirectory)
    commandLine("python3", "scripts/waxlabel-oracle.py", "--verify")
    environment("SISTRUM_WAXLABEL_ORACLE_DIR", oracleRoot.get())
}
tasks.test {
    dependsOn(verifyWaxLabelCorpus)
    systemProperty("waxlabel.cache", oracleRoot.get())
    systemProperty("waxlabel.repo", rootProject.layout.projectDirectory.asFile.absolutePath)
    inputs.file(rootProject.layout.projectDirectory.file("docs/waxlabel/corpus-manifest.tsv"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/waxlabel/oracle-step1.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/waxlabel/oracle-step2.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/waxlabel/oracle-step3.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/waxlabel/oracle-step4.json"))
    inputs.file(rootProject.layout.projectDirectory.file("docs/waxlabel/oracle-step5.json"))
    maxHeapSize = "512m"
}
tasks.register<JavaExec>("benchmark") {
    dependsOn(tasks.testClasses, verifyWaxLabelCorpus)
    classpath = sourceSets.test.get().runtimeClasspath
    systemProperty("waxlabel.step", providers.gradleProperty("waxlabelStep").getOrElse("1"))
    mainClass.set("me.misa198.airmedy.metadata.MetadataBenchmark")
    systemProperty("waxlabel.cache", oracleRoot.get())
    systemProperty("waxlabel.repo", rootProject.layout.projectDirectory.asFile.absolutePath)
    args(layout.buildDirectory.file("reports/metadata-benchmark.tsv").get().asFile.absolutePath)
    maxHeapSize = "512m"
}
