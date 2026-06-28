plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "2.3.1"  // 从 3.0.2 降级到 2.3.1
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)   // 改为 21
}

tasks {
    runServer {
        minecraftVersion("1.20.1")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}