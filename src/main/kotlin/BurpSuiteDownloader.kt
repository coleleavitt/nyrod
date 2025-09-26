import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

data class DownloadResult(
    val success: Boolean,
    val filePath: String? = null,
    val error: String? = null
)

data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val percentage: Int,
    val speed: String
)


class BurpSuiteDownloader {

    @Volatile
    private var cancelled = false

    fun fetchLatestVersion(): Triple<String, String, String>? {
        return try {
            val url = URI.create("https://portswigger.net/burp/releases/data?pageSize=5").toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36")

            if (connection.responseCode != 200) {
                return null
            }

            val jsonResponse = connection.inputStream.bufferedReader().readText()

            // Parse JSON to find Burp Pro JAR version and checksums
            val proJarPattern = """"builds":\[(.*?)\]""".toRegex()
            val proJarMatch = proJarPattern.find(jsonResponse)

            if (proJarMatch != null) {
                val buildsJson = proJarMatch.groups[1]?.value
                if (buildsJson != null) {
                    // Look for the pro JAR build entry
                    val jarBuildPattern = """\{[^}]*"ProductId":"pro"[^}]*"ProductPlatform":"Jar"[^}]*\}""".toRegex()
                    val jarBuildMatch = jarBuildPattern.find(buildsJson)

                    if (jarBuildMatch != null) {
                        val jarBuildJson = jarBuildMatch.value

                        // Extract version and SHA256
                        val versionPattern = """"Version":"([^"]+)"""".toRegex()
                        val sha256Pattern = """"Sha256Checksum":"([^"]+)"""".toRegex()

                        val versionMatch = versionPattern.find(jarBuildJson)
                        val sha256Match = sha256Pattern.find(jarBuildJson)

                        if (versionMatch != null && sha256Match != null) {
                            val version = versionMatch.groups[1]?.value!!
                            val sha256 = sha256Match.groups[1]?.value!!
                            val downloadUrl = "https://portswigger-cdn.net/burp/releases/download?product=pro&type=Jar&version=$version"

                            return Triple(version, downloadUrl, sha256)
                        }
                    }
                }
            }

            null
        } catch (e: Exception) {
            null
        }
    }

    fun downloadBurpSuite(progressCallback: (DownloadProgress) -> Unit): DownloadResult {
        return try {
            val targetDir = File(System.getProperty("user.home"), ".local/share/BurpSuite")
            targetDir.mkdirs()

            // Fetch latest version dynamically
            progressCallback(DownloadProgress(0, 0, 0, "Checking latest version..."))
            val versionInfo = fetchLatestVersion()

            if (versionInfo == null) {
                // Fallback to hardcoded version if API fails (no checksum validation)
                return downloadWithVersion("2025.9.3", listOf(
                    "https://portswigger-cdn.net/burp/releases/burpsuite_pro_v2025.9.3.jar",
                    "https://portswigger.net/burp/releases/burpsuite_pro_v2025.9.3.jar",
                    "https://portswigger-cdn.net/burp/releases/download?product=pro&version=2025.9.3&type=Jar"
                ), progressCallback, null)
            }

            val (version, apiUrl, expectedSha256) = versionInfo
            val targetFile = File(targetDir, "burpsuite_pro_v$version.jar")

            // Check if file exists and validate its integrity
            if (targetFile.exists() && targetFile.length() > 1000000) {
                progressCallback(DownloadProgress(0, 0, 0, "Validating existing file..."))
                val actualSha256 = calculateSHA256(targetFile)

                if (actualSha256.equals(expectedSha256, ignoreCase = true)) {
                    // File exists and is valid
                    val fileSize = targetFile.length()
                    progressCallback(DownloadProgress(fileSize, fileSize, 100, "File verified - already downloaded"))
                    return DownloadResult(true, targetFile.absolutePath)
                } else {
                    // File is corrupted, delete and re-download
                    progressCallback(DownloadProgress(0, 0, 0, "File corrupted - will re-download"))
                    targetFile.delete()
                }
            }

            // Clean up any incomplete download
            if (targetFile.exists()) {
                targetFile.delete()
            }

            // Try multiple download URLs like the original DownloadManager
            val downloadUrls = listOf(
                apiUrl, // API-provided URL first
                "https://portswigger-cdn.net/burp/releases/burpsuite_pro_v$version.jar",
                "https://portswigger.net/burp/releases/burpsuite_pro_v$version.jar"
            )

            return downloadWithVersion(version, downloadUrls, progressCallback, expectedSha256)

        } catch (e: Exception) {
            DownloadResult(false, error = e.message)
        }
    }

    private fun downloadWithVersion(version: String, urls: List<String>, progressCallback: (DownloadProgress) -> Unit, expectedSha256: String?): DownloadResult {
        return try {
            val targetDir = File(System.getProperty("user.home"), ".local/share/BurpSuite")
            val targetFile = File(targetDir, "burpsuite_pro_v$version.jar")

            for (url in urls) {
                try {
                    val result = performRealDownload(url, targetFile, progressCallback, expectedSha256)
                    if (result.success) {
                        return result
                    }
                } catch (e: Exception) {
                    // Try next URL
                    continue
                }
            }

            // If all downloads fail
            DownloadResult(false, error = "Unable to download from PortSwigger servers. Please check your internet connection or download manually from https://portswigger.net/burp/releases")

        } catch (e: Exception) {
            DownloadResult(false, error = e.message)
        }
    }

    private fun performRealDownload(
        urlString: String,
        targetFile: File,
        progressCallback: (DownloadProgress) -> Unit,
        expectedSha256: String?
    ): DownloadResult {
        return try {
            val url = URI.create(urlString).toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 30000  // Increased timeout like original
            connection.readTimeout = 30000     // Increased timeout like original
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36")
            connection.instanceFollowRedirects = true

            // Check response code
            val responseCode = connection.responseCode
            if (responseCode != 200) {
                return DownloadResult(false, error = "HTTP $responseCode: ${connection.responseMessage}")
            }

            val contentLength = connection.contentLengthLong
            if (contentLength <= 0) {
                return DownloadResult(false, error = "Unable to determine file size from $urlString")
            }

            // Check if this is actually a JAR file (should be > 100MB for Burp Suite)
            if (contentLength < 100_000_000) {
                return DownloadResult(false, error = "File too small (${contentLength} bytes) - likely not the real JAR")
            }

            connection.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    var totalBytesRead = 0L
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    val startTime = System.currentTimeMillis()
                    var lastUpdateTime = startTime

                    progressCallback(DownloadProgress(0, contentLength, 0, "Starting..."))

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (cancelled) {
                            targetFile.delete()
                            return DownloadResult(false, error = "Download cancelled")
                        }

                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastUpdateTime > 500) { // Update every 500ms
                            val elapsedSeconds = (currentTime - startTime) / 1000.0
                            val bytesPerSecond = if (elapsedSeconds > 0) totalBytesRead / elapsedSeconds else 0.0
                            val speed = formatSpeed(bytesPerSecond.toLong())

                            val percentage = ((totalBytesRead * 100) / contentLength).toInt()
                            progressCallback(DownloadProgress(totalBytesRead, contentLength, percentage, speed))

                            lastUpdateTime = currentTime
                        }
                    }

                    progressCallback(DownloadProgress(totalBytesRead, contentLength, 100, "Download complete"))

                    // Validate checksum if available
                    if (expectedSha256 != null) {
                        progressCallback(DownloadProgress(totalBytesRead, contentLength, 100, "Validating checksum..."))

                        val actualSha256 = calculateSHA256(targetFile)
                        if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                            // Checksum mismatch - file is corrupted
                            targetFile.delete()
                            return DownloadResult(false, error = "Checksum validation failed. Expected: $expectedSha256, Got: $actualSha256")
                        }

                        progressCallback(DownloadProgress(totalBytesRead, contentLength, 100, "File validated successfully"))
                    }

                    DownloadResult(true, targetFile.absolutePath)
                }
            }
        } catch (e: Exception) {
            if (targetFile.exists()) {
                targetFile.delete()
            }
            DownloadResult(false, error = "Download from $urlString failed: ${e.message}")
        }
    }

    private fun performSimulatedDownload(progressCallback: (DownloadProgress) -> Unit) {
        // Simulate a realistic download (Burp Suite Pro is ~200MB)
        val totalSize = 204800L * 1024 // ~200MB

        progressCallback(DownloadProgress(0, totalSize, 0, "Demo mode..."))
        Thread.sleep(800)

        // Simulate progressive download with realistic speeds
        val chunks = listOf(
            Pair(10240L * 1024, "2.5 MB/s"), // 10MB
            Pair(25600L * 1024, "3.1 MB/s"), // 25MB
            Pair(51200L * 1024, "4.2 MB/s"), // 50MB
            Pair(76800L * 1024, "5.0 MB/s"), // 75MB
            Pair(102400L * 1024, "5.5 MB/s"), // 100MB
            Pair(128000L * 1024, "6.0 MB/s"), // 125MB
            Pair(153600L * 1024, "5.8 MB/s"), // 150MB
            Pair(179200L * 1024, "6.2 MB/s"), // 175MB
            Pair(totalSize, "6.5 MB/s")       // 200MB
        )

        for ((downloaded, speed) in chunks) {
            val percentage = ((downloaded * 100) / totalSize).toInt()
            progressCallback(DownloadProgress(downloaded, totalSize, percentage, speed))
            Thread.sleep(1200) // Realistic download timing
        }

        progressCallback(DownloadProgress(totalSize, totalSize, 100, "Demo complete"))
    }

    private fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond >= 1024 * 1024 -> "%.1f MB/s".format(bytesPerSecond / (1024.0 * 1024.0))
            bytesPerSecond >= 1024 -> "%.1f KB/s".format(bytesPerSecond / 1024.0)
            else -> "$bytesPerSecond B/s"
        }
    }

    fun calculateSHA256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { inputStream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun cancel() {
        cancelled = true
    }

    fun getCurrentVersion(): String? {
        return fetchLatestVersion()?.first
    }

    fun getInstalledVersion(): String? {
        val targetDir = File(System.getProperty("user.home"), ".local/share/BurpSuite")

        // Look for any burpsuite_pro_v*.jar file
        val jarFiles = targetDir.listFiles { _, name ->
            name.startsWith("burpsuite_pro_v") && name.endsWith(".jar")
        } ?: return null

        return jarFiles.maxByOrNull { it.lastModified() }?.let { file ->
            // Extract version from filename like burpsuite_pro_v2025.9.3.jar
            val versionRegex = """burpsuite_pro_v(.+)\.jar""".toRegex()
            versionRegex.find(file.name)?.groupValues?.get(1)
        }
    }
}