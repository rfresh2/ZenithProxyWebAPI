plugins {
    id("zenithproxy.plugin.dev") version "1.0.0-SNAPSHOT"
}

group = properties["maven_group"] as String
version = properties["plugin_version"] as String
val mc = properties["mc"] as String

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

zenithProxyPlugin {
    templateProperties = mapOf(
        "version" to project.version
    )
    javaReleaseVersion = JavaLanguageVersion.of(21)
}

repositories {
    maven("https://maven.2b2t.vc/releases") {
        description = "ZenithProxy Releases and Dependencies"
    }
    maven("https://maven.2b2t.vc/remote") {
        description = "Dependencies used by ZenithProxy"
    }
}

dependencies {
    zenithProxy("com.zenith:ZenithProxy:$mc-SNAPSHOT")
    shade("io.javalin:javalin:6.7.0")

    // todo: remove when javalin updates to jackson 3
    shade("com.fasterxml.jackson.core:jackson-core:2.20.1")
    shade("com.fasterxml.jackson.core:jackson-databind:2.20.1")
    shade("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.20.1")
}

tasks {
    shadowJar {
        val shadowPackage = "dev.zenith.web.shadow"
        relocate("io.javalin", "$shadowPackage.javalin")
        relocate("jakarta.servlet", "$shadowPackage.jakarta.servlet")
        relocate("kotlin", "$shadowPackage.kotlin")
        relocate("org.eclipse", "$shadowPackage.org.eclipse")
        exclude("META-INF/maven/**")
        // todo: transform service files? seems to work fine without them for now
        dependencies {
            exclude(dependency("org.slf4j:.*:.*"))
            exclude(dependency("org.jetbrains:annotations:.*:.*"))
        }
    }
}
