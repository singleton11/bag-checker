plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

application {
    mainClass = "MainKt"
}

val redisHost = "localhost"
val apiKey = project.findProperty("apiKey").toString()

val run by tasks.getting(JavaExec::class) {
    args = listOf(apiKey, redisHost)
}