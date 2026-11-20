plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "cn.inovance"
version = "1.0.6"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

intellij {
    version.set("2023.3")
    type.set("IC")                        // IntelliJ IDEA Community
    pluginName.set("Oracle PL/SQL Formatter")
    updateSinceUntilBuild.set(false)
}

tasks {
    patchPluginXml {
        sinceBuild.set("231")             // 2023.1+
        untilBuild.set("263.*")           // 覆盖 IDEA 2026.1 (build 261)
    }
    // 不需要为设置页生成可搜索选项索引，避免构建期启动 IDE
    buildSearchableOptions {
        enabled = false
    }
    test {
        useJUnitPlatform()
    }
}
