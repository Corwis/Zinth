plugins {
    `java-library`
    `maven-publish`
}

group = "net.zanoria"
version = "1.0-SNAPSHOT"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.jetbrains:annotations:24.0.0")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "zinth-api"
            from(components["java"])
        }
    }
}
