plugins {
    id("zenithproxy.plugin.dev") version "1.0.0-SNAPSHOT"
}

group = properties["maven_group"] as String
version = properties["plugin_version"] as String
val mc = properties["mc"] as String

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

zenithProxyPlugin {
    templateProperties = mapOf(
        "version" to project.version
    )
}

repositories {
    mavenLocal()
    maven("https://maven.2b2t.vc/releases") {
        description = "ZenithProxy Releases and Dependencies"
    }
    maven("https://maven.2b2t.vc/remote") {
        description = "Dependencies used by ZenithProxy"
    }
}

dependencies {
    zenithProxy("com.zenith:ZenithProxy:$mc-SNAPSHOT")
    shade("io.javalin:javalin:6.6.0")
}

tasks {
    shadowJar {
        val shadowPackage = "dev.zenith.web.shadow"
        relocate("io.javalin", "$shadowPackage.javalin")
        relocate("jakarta.servlet", "$shadowPackage.jakarta.servlet")
        relocate("kotlin", "$shadowPackage.kotlin")
        relocate("org.eclipse", "$shadowPackage.org.eclipse")
        exclude("org/slf4j/**")
        exclude("org/jetbrains/**")
        exclude("META-INF/maven/**")
        // todo: transform service files? seems to work fine without them for now
    }
}
