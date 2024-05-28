plugins {
    kotlin("jvm") version "1.9.23"
    id("org.jetbrains.intellij") version "1.17.3"
}

group = "com.guthub.nizienko"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.open-meteo:open-meteo-api-kotlin:0.7.1-beta.1")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}

intellij {
    version.set("2024.1")
    type.set("IC")
}

tasks {
    patchPluginXml {
        sinceBuild.set("233.1")
        untilBuild.set("242.*")
    }
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
}