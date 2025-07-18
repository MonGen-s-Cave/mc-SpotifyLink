plugins {
    id("java")
    id("com.gradleup.shadow") version "8.3.2"
    id("io.github.revxrsal.zapper") version "1.0.2"
    id("io.freefair.lombok") version "8.11"
}

group = "com.mongenscave"
version = "1.0.0"

repositories {
    mavenCentral()

    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("org.projectlombok:lombok:1.18.36")
    compileOnly("me.clip:placeholderapi:2.11.6")

    implementation("io.github.revxrsal:lamp.bukkit:4.0.0-rc.12") {
        exclude(module = "lamp.common")
        exclude(module = "lamp.brigadier")
    }

    zap("com.github.User-19fff:EasierMessages:81355a2cb6")
    zap("io.github.revxrsal:lamp.common:4.0.0-rc.12")
    zap("io.github.revxrsal:lamp.brigadier:4.0.0-rc.12")
    zap("com.github.Anon8281:UniversalScheduler:0.1.6")
    zap("dev.dejvokep:boosted-yaml:1.3.6")
    zap("mysql:mysql-connector-java:8.0.33")
    zap("com.zaxxer:HikariCP:6.2.1")
    zap("com.google.code.gson:gson:2.10.1")
    zap("org.apache.httpcomponents:httpclient:4.5.14")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

zapper {
    libsFolder = "libs"
    relocationPrefix = "com.mongenscave.mcspotifylink"

    repositories { includeProjectRepositories() }

    relocate("com.github.Anon8281.universalScheduler", "universalScheduler")
    relocate("dev.dejvokep.boostedyaml", "boostedyaml")
    relocate("com.google.gson", "gson")
    relocate("org.apache.http", "http")
}