// SPDX-License-Identifier: GPL-3.0-or-later
plugins { kotlin("jvm") }
kotlin { jvmToolchain(21) }
dependencies { testImplementation(libs.junit); testImplementation("com.google.code.gson:gson:2.13.2") }
tasks.jar { from("THIRD-PARTY-NOTICES") { into("META-INF") } }
tasks.withType<Test>().configureEach {
    systemProperty("dsp.repo", rootProject.projectDir.absolutePath)
    inputs.files(rootProject.fileTree("docs/dsp") { include("*-oracle.json", "fft-jvm-before.tsv") })
}
tasks.register<JavaExec>("benchmark") {
 dependsOn(tasks.testClasses)
 classpath = sourceSets.test.get().runtimeClasspath
 mainClass.set("com.amwolfstein.sistrum.dsp.DspBenchmark")
}
