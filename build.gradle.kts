plugins {
    kotlin("jvm") version "1.9.22"
    id("org.openjfx.javafxplugin") version "0.1.0"
    application
}

group = "burploader"
version = "1.0.0"

repositories {
    mavenCentral()
}

// Configure JavaFX modules
javafx {
    version = "21.0.1"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.web")
}

dependencies {
    // Updated ASM dependencies for Java 21 support (class file version 65)
    implementation("org.ow2.asm:asm:9.6")
    implementation("org.ow2.asm:asm-tree:9.6")

    // JavaFX dependencies are handled by the plugin
    // But we can add additional ones if needed
    implementation("org.controlsfx:controlsfx:11.1.2") // Additional controls
}

application {
    mainClass.set("BurpKeygenAppKt")
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

// Configure JVM arguments for Wayland support
tasks.withType<JavaExec> {
    // Enable Wayland support
    systemProperty("prism.order", "sw,d3d")  // Software rendering fallback
    systemProperty("javafx.platform", "monocle")  // Enable headless support if needed

    // For better Wayland performance
    jvmArgs = listOf(
        "--add-exports=javafx.base/com.sun.javafx.runtime=ALL-UNNAMED",
        "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
        "--add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED"
    )
}

// Run task configuration
tasks.named<JavaExec>("run") {
    dependsOn(tasks.classes)
}