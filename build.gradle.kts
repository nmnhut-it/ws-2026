plugins { application }

repositories { mavenCentral() }

dependencies { implementation(files("lib/gamelogic.jar")) }

val beastThreshold = (project.findProperty("threshold") ?: "3").toString()

sourceSets {
    main { java.setSrcDirs(listOf("src")) }
    create("tests") {
        java.setSrcDirs(listOf("tests/src"))
        compileClasspath += sourceSets.main.get().output + configurations.runtimeClasspath.get()
        runtimeClasspath += output + compileClasspath
    }
}

application {
    mainClass = "GameServer"
    applicationDefaultJvmArgs = listOf("-Dbeast.threshold=$beastThreshold", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }

tasks.named<JavaExec>("run") {
    args = listOf((project.findProperty("port") ?: "9000").toString())
    standardInput = System.`in`
}

tasks.register<JavaExec>("tests") {
    group = "verification"
    description = "Chay tests/src/Tests.java (Java thuan, khong JUnit)"
    classpath = sourceSets["tests"].runtimeClasspath
    mainClass = "Tests"
    jvmArgs = listOf("-Dstdout.encoding=UTF-8")
}
