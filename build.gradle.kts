plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    application
}

group = "burploader"
version = "1.0.0"

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    // Updated ASM dependencies for Java 21 support (class file version 65)
    implementation("org.ow2.asm:asm:9.6")
    implementation("org.ow2.asm:asm-tree:9.6")

    // java-gtk for native GTK 4 Wayland support
    implementation("com.github.bailuk:java-gtk:0.6.1")

    // Kotlin serialization for JSON parsing
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
}

application {
    mainClass.set("GtkBurpKeygenAppKt")
}



// --- CRITICAL CONFIGURATION FOR THE JAVA AGENT ---
// This ensures the final JAR is a valid agent
tasks.jar {
    manifest {
        attributes("Premain-Class" to "LoaderKt")
    }
    // Clean up the output JAR name to be "burploader.jar"
    archiveBaseName.set("burploader")
    archiveVersion.set("")
    archiveClassifier.set("")
}

// Create a fat JAR that includes all dependencies
tasks.register<Jar>("fatJar") {
    manifest {
        attributes("Premain-Class" to "LoaderKt")
        attributes("Main-Class" to "BurpKeygenAppKt")
    }
    archiveBaseName.set("burploader")
    archiveVersion.set("")
    archiveClassifier.set("fat")

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get() as CopySpec)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// GTK 4 native Wayland support configuration
tasks.withType<JavaExec> {
    // Native Wayland environment
    environment("GDK_BACKEND", "wayland")
    environment("WAYLAND_DISPLAY", "wayland-1")
    environment("XDG_SESSION_TYPE", "wayland")

    // GTK 4 Wayland-specific configuration
    systemProperty("java.awt.headless", "false")

    // JVM arguments for JNA and native access
    jvmArgs = listOf(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
    )
}

// Make run task depend on fatJar to ensure it's built
tasks.named("run") {
    dependsOn("fatJar")
}

// Remove duplicate run task configuration since Compose Desktop handles it