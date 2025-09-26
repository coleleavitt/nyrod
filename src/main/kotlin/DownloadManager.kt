import javafx.application.Platform
import javafx.concurrent.Task
import javafx.geometry.Insets
import javafx.scene.control.*
import javafx.scene.layout.VBox
import javafx.stage.Stage
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

object DownloadManager {

    private val BURP_DIR = getBurpSuiteDirectory()

    fun downloadBurpSuite(version: String, downloadUrl: String, parentStage: Stage): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()

        // Create progress dialog
        val dialog = createProgressDialog(parentStage)
        val progressBar = dialog.dialogPane.content.let { it as VBox }.children[1] as ProgressBar
        val statusLabel = (dialog.dialogPane.content as VBox).children[2] as Label

        val downloadTask = object : Task<Boolean>() {
            override fun call(): Boolean {
                return try {
                    // Ensure the download directory exists
                    Files.createDirectories(BURP_DIR)

                    // Remove old versions first
                    cleanupOldVersions(version)

                    val fileName = "burpsuite_pro_v$version.jar"
                    val targetFile = BURP_DIR.resolve(fileName)

                    updateMessage("Connecting to download server...")
                    info("Starting download: $downloadUrl -> $targetFile")

                    val url = URI.create(downloadUrl).toURL()
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 30000
                    connection.readTimeout = 30000

                    val contentLength = connection.contentLength.toLong()
                    info("Content length: $contentLength bytes")

                    connection.inputStream.use { input ->
                        Files.newOutputStream(targetFile).use { output ->
                            val buffer = ByteArray(8192)
                            var totalBytesRead = 0L
                            var bytesRead: Int

                            updateMessage("Downloading Burp Suite v$version...")

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                if (isCancelled) {
                                    Files.deleteIfExists(targetFile)
                                    return false
                                }

                                output.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead

                                if (contentLength > 0) {
                                    val progress = totalBytesRead.toDouble() / contentLength
                                    updateProgress(progress, 1.0)
                                    updateMessage("Downloaded ${formatBytes(totalBytesRead)} / ${formatBytes(contentLength)}")
                                } else {
                                    updateMessage("Downloaded ${formatBytes(totalBytesRead)}")
                                }
                            }
                        }
                    }

                    updateMessage("Download completed successfully!")
                    updateProgress(1.0, 1.0)
                    info("Download completed: $targetFile")

                    true
                } catch (e: Exception) {
                    error("Download failed: ${e.message}")
                    updateMessage("Download failed: ${e.message}")
                    false
                }
            }
        }

        // Bind progress
        progressBar.progressProperty().bind(downloadTask.progressProperty())
        statusLabel.textProperty().bind(downloadTask.messageProperty())

        // Handle task completion
        downloadTask.setOnSucceeded {
            dialog.close()
            val success = downloadTask.value
            if (success) {
                showDownloadSuccessDialog(parentStage, version)
                info("Burp Suite v$version downloaded and installed successfully")
            } else {
                showDownloadErrorDialog(parentStage, "Failed to download Burp Suite v$version")
            }
            future.complete(success)
        }

        downloadTask.setOnFailed {
            dialog.close()
            val error = downloadTask.exception?.message ?: "Unknown error"
            showDownloadErrorDialog(parentStage, "Error downloading Burp Suite v$version: $error")
            future.complete(false)
        }

        downloadTask.setOnCancelled {
            dialog.close()
            info("Download cancelled by user")
            future.complete(false)
        }

        // Show the dialog and start download
        dialog.show()
        Thread(downloadTask).start()

        return future
    }

    private fun createProgressDialog(parentStage: Stage): Dialog<Void> {
        val dialog = Dialog<Void>()
        dialog.title = "Downloading Burp Suite"
        dialog.initOwner(parentStage)

        val content = VBox(15.0)
        content.padding = Insets(20.0)

        val titleLabel = Label("Downloading Burp Suite Pro")
        titleLabel.styleClass.add("dialog-title")

        val progressBar = ProgressBar(0.0)
        progressBar.prefWidth = 400.0
        progressBar.styleClass.add("download-progress")

        val statusLabel = Label("Preparing download...")
        statusLabel.styleClass.add("status-label")

        content.children.addAll(titleLabel, progressBar, statusLabel)
        dialog.dialogPane.content = content

        // Add cancel button
        val cancelButton = ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE)
        dialog.dialogPane.buttonTypes.add(cancelButton)

        return dialog
    }

    private fun cleanupOldVersions(newVersion: String) {
        try {
            if (!Files.exists(BURP_DIR)) return

            Files.list(BURP_DIR).use { files ->
                files.filter { file ->
                    val fileName = file.fileName.toString()
                    fileName.startsWith("burpsuite_pro_v") &&
                    fileName.endsWith(".jar") &&
                    !fileName.contains(newVersion)
                }.forEach { oldFile ->
                    try {
                        Files.delete(oldFile)
                        info("Removed old version: ${oldFile.fileName}")
                    } catch (e: Exception) {
                        warn("Failed to delete old version ${oldFile.fileName}: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            warn("Failed to cleanup old versions: ${e.message}")
        }
    }

    private fun getBurpSuiteDirectory(): Path {
        val os = System.getProperty("os.name").lowercase()
        val userHome = System.getProperty("user.home")

        return when {
            os.contains("win") -> {
                // Windows: %LOCALAPPDATA%\BurpSuite or %USERPROFILE%\AppData\Local\BurpSuite
                val localAppData = System.getenv("LOCALAPPDATA") ?: "$userHome\\AppData\\Local"
                Paths.get(localAppData, "BurpSuite")
            }
            os.contains("mac") -> {
                // macOS: ~/Library/Application Support/BurpSuite
                Paths.get(userHome, "Library", "Application Support", "BurpSuite")
            }
            else -> {
                // Linux/Unix: ~/.local/share/BurpSuite
                Paths.get(userHome, ".local", "share", "BurpSuite")
            }
        }
    }

    fun getBurpSuiteJarPath(): Path? {
        if (!Files.exists(BURP_DIR)) return null

        return try {
            Files.list(BURP_DIR).use { files ->
                files.filter { file ->
                    val fileName = file.fileName.toString()
                    fileName.startsWith("burpsuite_pro_v") && fileName.endsWith(".jar")
                }.findFirst().orElse(null)
            }
        } catch (e: Exception) {
            warn("Failed to find Burp Suite JAR: ${e.message}")
            null
        }
    }

    fun getInstalledVersion(): String? {
        val jarPath = getBurpSuiteJarPath() ?: return null
        val fileName = jarPath.fileName.toString()

        return try {
            // Extract version from filename like "burpsuite_pro_v2024.1.1.jar"
            val versionPattern = Regex("burpsuite_pro_v([\\d.]+)\\.jar")
            versionPattern.find(fileName)?.groupValues?.get(1)
        } catch (e: Exception) {
            warn("Failed to extract version from filename: $fileName")
            null
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    private fun showDownloadSuccessDialog(parentStage: Stage, version: String) {
        Platform.runLater {
            val alert = Alert(Alert.AlertType.INFORMATION)
            alert.initOwner(parentStage)
            alert.title = "Download Complete"
            alert.headerText = "Burp Suite Pro v$version"
            alert.contentText = "Burp Suite has been successfully downloaded and installed to:\n${BURP_DIR}\n\nThe application is ready to use!"
            alert.showAndWait()
        }
    }

    private fun showDownloadErrorDialog(parentStage: Stage, message: String) {
        Platform.runLater {
            val alert = Alert(Alert.AlertType.ERROR)
            alert.initOwner(parentStage)
            alert.title = "Download Failed"
            alert.headerText = null
            alert.contentText = message
            alert.showAndWait()
        }
    }
}