plugins {
    kotlin("jvm") version "1.9.23"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "com.guthub.nizienko"
version = "1.0.6"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3")
        bundledPlugin("org.intellij.plugins.markdown")
        pluginVerifier()
    }
    testImplementation(kotlin("test"))
    implementation("com.open-meteo:open-meteo-api-kotlin:0.7.1-beta.1")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.test {
    useJUnitPlatform()
}

intellijPlatform {
    pluginVerification {
        ides {
            recommended()
        }
    }
    pluginConfiguration {
        ideaVersion {
            sinceBuild.set("242.1")
        }
    }
}