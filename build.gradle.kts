plugins {
    id("zenithproxy.plugin.dev") version "1.0.1-SNAPSHOT"
}

group = property("maven_group") as String
version = property("plugin_version") as String
val mc = property("mc") as String
val pluginId = property("plugin_id") as String

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

zenithProxyPlugin {
    templateProperties = mapOf(
        "version" to project.version,
        "mc_version" to mc,
        "plugin_id" to pluginId,
        "maven_group" to group as String,
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
    shade("io.javalin:javalin:7.2.3")
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
            exclude(dependency("org.ow2.asm:.*:.*"))
        }
    }
}
