import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.*
import java.util.logging.ConsoleHandler
import java.util.logging.Level
import java.util.logging.Logger
import java.util.logging.SimpleFormatter
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties
import java.util.Base64

private const val CONFIG_FILE_NAME = ".config.ini"

val logger: Logger = Logger.getLogger("BurpKeygen").apply {
    level = Level.ALL
    val handler = ConsoleHandler().apply {
        level = Level.ALL
        formatter = SimpleFormatter()
    }
    addHandler(handler)
    useParentHandlers = false
}

fun info(message: String) = logger.info(message)
fun warn(message: String) = logger.warning(message)
fun error(message: String) = logger.severe(message)
fun debug(message: String) = logger.fine(message)

object ConfigManager {
    private val configFile = File(CONFIG_FILE_NAME)
    private val properties = Properties()

    init {
        loadConfig()
    }

    private fun loadConfig() {
        if (configFile.exists()) {
            try {
                FileInputStream(configFile).use { input ->
                    properties.load(input)
                }
                info("Configuration loaded from ${configFile.name}")
            } catch (e: Exception) {
                warn("Failed to load configuration: ${e.message}")
            }
        } else {
            info("Configuration file not found, using defaults")
        }
    }

    private fun saveConfig() {
        try {
            FileOutputStream(configFile).use { output ->
                properties.store(output, "Nyrod")
            }
            info("Configuration saved to ${configFile.name}")
        } catch (e: Exception) {
            error("Failed to save configuration: ${e.message}")
        }
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return properties.getProperty(key, defaultValue.toString()).toBoolean()
    }

    fun putBoolean(key: String, value: Boolean) {
        properties.setProperty(key, value.toString())
        saveConfig()
    }

    fun getString(key: String, defaultValue: String): String {
        return properties.getProperty(key, defaultValue)
    }

    fun putString(key: String, value: String) {
        properties.setProperty(key, value)
        saveConfig()
    }

    fun saveLicenseSettings(
        licenseName: String,
        companyName: String,
        email: String,
        licenseType: String,
        expirationDate: String?, // ISO date string or null
        licenseId: String,
        notes: String
    ) {
        putString("license_name", licenseName)
        putString("company_name", companyName)
        putString("email", email)
        putString("license_type", licenseType)
        putString("expiration_date", expirationDate ?: "")
        putString("license_id", licenseId)
        putString("notes", notes)
    }

    fun getLicenseSettings(): Map<String, String> {
        return mapOf(
            "license_name" to getString("license_name", "Licensed to John Doe"),
            "company_name" to getString("company_name", ""),
            "email" to getString("email", ""),
            "license_type" to getString("license_type", "PROFESSIONAL"),
            "expiration_date" to getString("expiration_date", ""),
            "license_id" to getString("license_id", ""),
            "notes" to getString("notes", "")
        )
    }
}

private const val encodedPrivateKey2048 = "MzA4MjAyMzgwMjAxMDAzMDBEMDYwOTJBODY0ODg2RjcwRDAxMDEwMTA1MDAwNDgyMDIyMjMwODIwMjFFMDIwMTAwMDI4MjAxMDEwMDlEOURGOUVCNDk4OTBERThGMTkzQzg5NTk4NTg0QkM5NDdCQTgzNzI3QjJEODlBQThCRTNBNDY4OTEzMEZFMkU5NDg5NjdENDBCNjU2NzYyRjk5ODlFNTlDOTY1NUUyOEUzM0ZENEI0QTU0NDEyNkZERDkwQTU2NkJCNjFDMkQ3Qzc0QTY4MjkyNjU3NjdCNTZFMjhGRDIyMTRENEJFQjNCMURBNDcyMkJDMzk0RTJFNkFGQTBGMTY4OUZBOURCNDQyNjQzREREQTg0OTk3QzVBRDE1QjU3RUU1QkQxQTM1N0NBQkY2RUQ0Q0FBQTVGQjg4NzJFMDdDOEY1RkFFMUM1NzNDMTIxNEREMjczQzZEODg4N0Q3RTk5MzIwOEQ3NTExOENDMjMwNUQ2MEFBMzM3QjA5OTlCNjk5ODgzMjJBOEZBQTlGQkZGNDlBQjcwQjcxNzIzRTFDQkQ3OUQxMjY0MEFGMTlFNkZCQzI4QzA1RTY2MzA0MTREQkFEOUFFRjkxMkQwQUM1M0U0MEI3RjQ4RUUyOUJGRTFERUZDRkIwQkRCMUI2QzVCRjhCMDZEQ0NBMTVGQTFGQzNGNDY4OTUyRDQ4MTA3MEM5MkMzODZEM0NFNjE4N0IwNjIwMzhBNkNBODIyRDM1MkVDRUJFQUMxOTU5MThGOUJCNUMzQUMzMDIwMTAwMDI4MjAxMDA1REFENzFDNzU0QkEzRjY5MkU4MzVFMTkwMzI1OUY0RDZFRjMzQzgyQzMxMTBBOUMzMTZFNDdERERBNDU1QjFEMDYyRDMwNjc4N0FBNkEyQjFBMUI4QTI5RTUxN0Y5NDFBNUU2REYxRENBODdDREM5NkNDRjM2NkVGQjc5OUMxQjMxMTg1OTE1RjNGMkM4RjFCRDFBNjE3MDZCMUYxMjg0QUM3NTA2MDg3MDA0NDMyMjM1NzQ4Rjk5MUVDMkI0MEU1OUQzNDgyREMwODI5NEQwRTkxMTU5MDBBNUJDQTFBMjFFODlGQTQ1ODk2Njc3MjYyQjJGRDM5QTU0ODA1MjczMTYyRDY1NUYxQUI0MzkyQ0U0RTAxQTRERDYzRjdFRjM4N0I3OUQ1M0I3M0JCRTQ1RUE3RDlCRTY0QTYyN0NGQjNEQUUyODQzRTg1RUQzNjk3NjcyQkQ0ODMyRjVFRUI0QzE4QzREMTVGRUI1NTBFMEI1QTcwMThBM0NEMzlBOUZENEJEQTM1QTZGODhCRDAwQ0NCQzc4NzQxOUFENTdDNTRGQTgyM0VDM0Q3NjYyNzEwQjAzQzI2MjJFOUUyREU1NDZCMjFDQTFDNzY2NzJCMUNDNkJEOTI4NzFBMEY5NjA1MUUzMUNCMDYwRTBEREI0MDIyQkVCMjg5N0E4ODc2MTAyMDEwMDAyMDEwMDAyMDEwMDAyMDEwMDAyMDEwMA=="
private const val encodedPrivateKey1024 = "MzA4MjAxMzYwMjAxMDAzMDBEMDYwOTJBODY0ODg2RjcwRDAxMDEwMTA1MDAwNDgyMDEyMDMwODIwMTFDMDIwMTAwMDI4MTgxMDA4RDE4NzIzM0VCODdBQjYwREI1QkFFODQ1M0E3REUwMzU0MjhFQjE3N0VDOEM2MDM0MUNBQjRDRjQ4NzA1Mjc1MUNBOEFGRjIyNkVBM0U5OEYwQ0VFRjhBQUUxMkUzNzE2QjhBMjBBMjRCREUyMDcwMzg2NUM5REJEOTU0M0Y5MkVBNjQ5NTc2M0RGRDZGNzUwN0I4NjA3RjJBMTRGNTI2OTRCQjk3OTNGRTEyRDNEOUM1RDFDMDA0NTI2MkVBNUU3RkE3ODJFRDQyNTY4QzZCN0UzMTAxOUZGRkFCQUVGQjc5RDMyN0E0QTdBQ0JENEQ1NDdBQ0IyREM5Q0QwNDAzMDIwMTAwMDI4MTgwNzE3MkExODhEQkFEOTc3RkU2ODBCRTNFQzlFMEU0RTMzQTREMzg1MjA4RjAzODNFQjAyQ0UzREFGMzNDRDUyMDMzMkRGMzYyQkEyNTg4QjU4MjkyNzEwQUM5RDI4ODJDNEYzMjlERjBDMTFERDY2OTQ0RkY5QjIxRjk4QTAzMUVEMjdDMTlGRTJCQ0Y4QTA5QUQzRTI1NEEwRkQ3QUI4OUUwRDFFNzU2QkNGMzdFRDI0RDQyRDE5NzdFQTdDMUM3OEFCRjREMTNGNzUyQUU0OEI0MjZBMkRDOThDNUQxM0IyMzEzNjA5RkFBNjQ0MUU4MzVEQzYxRDE3QTAxRDFBOTAyMDEwMDAyMDEwMDAyMDEwMDAyMDEwMDAyMDEwMA=="

internal val privateKey2048: String get() = String(Base64.getDecoder().decode(encodedPrivateKey2048))
internal val privateKey1024: String get() = String(Base64.getDecoder().decode(encodedPrivateKey1024))
private const val encodedEncryptionKey = "OTgsMTE3LDExNCwxMTIsMTE0LDQ4LDEyMCwzMw=="
internal val encryptionKey: ByteArray get() {
    val decoded = String(Base64.getDecoder().decode(encodedEncryptionKey))
    return decoded.split(",").map { it.toByte() }.toByteArray()
}

fun generateActivationForUI(activationRequest: String): String {
    return if (activationRequest.isBlank()) "" else generateActivation(activationRequest)
}

internal fun getSignatureBytes(list: List<String>): ByteArray {
    return ByteArrayOutputStream().use { byteArray ->
        list.forEach {
            byteArray.write(it.toByteArray())
            byteArray.write(0)
        }
        byteArray.toByteArray()
    }
}

internal fun getSign(pri: String, data: ByteArray, method: String): String {
    return try {
        val sign = java.security.Signature.getInstance(method)
        sign.initSign(getPriKeyByHex(pri))
        sign.update(data)
        val signature = sign.sign()
        Base64.getEncoder().encodeToString(signature)
    } catch (e: Exception) {
        e.printStackTrace()
        ""
    }
}

internal fun getRandomString(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890"
    val rnd = Random()
    return buildString(32) {
        repeat(32) {
            append(chars[rnd.nextInt(chars.length)])
        }
    }
}

internal fun prepareArray(list: List<String>): String {
    val byteArray = ByteArrayOutputStream().use { stream ->
        for (i in 0 until list.size - 1) {
            stream.write(list[i].toByteArray())
            stream.write(0)
        }
        stream.write(list.last().toByteArray())
        stream.toByteArray()
    }
    return Base64.getEncoder().encodeToString(encrypt(byteArray))
}

private fun encrypt(data: ByteArray): ByteArray {
    return try {
        val localSecretKeySpec = javax.crypto.spec.SecretKeySpec(encryptionKey, "DES")
        val localCipher = javax.crypto.Cipher.getInstance("DES")
        localCipher.init(javax.crypto.Cipher.ENCRYPT_MODE, localSecretKeySpec)
        localCipher.doFinal(data)
    } catch (e: Exception) {
        e.printStackTrace()
        throw RuntimeException(e)
    }
}


fun getHTTPBody(urlString: String): String {
    return try {
        info("Fetching URL: $urlString")
        val url = URI.create(urlString).toURL()
        val https = url.openConnection() as HttpsURLConnection
        https.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36")
        https.connectTimeout = 10000
        https.readTimeout = 10000

        val responseCode = https.responseCode
        info("HTTP Response Code: $responseCode")

        if (responseCode == 200) {
            val content = https.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            info("Response received, length: ${content.length}")
            content
        } else {
            warn("HTTP request failed with code: $responseCode")
            ""
        }
    } catch (e: Exception) {
        error("Error fetching HTTP content: ${e.message}")
        e.printStackTrace()
        ""
    }
}

fun getLatestVersion(): String {
    try {
        info("Checking for latest Burp Suite version...")
        val result = getHTTPBody("https://portswigger.net/burp/releases/data?pageSize=5")

        if (result.isEmpty()) {
            warn("Empty response from version check")
            return ""
        }

        info("Raw response preview: ${result.take(200)}...")

        // Try multiple patterns to find the version
        val patterns = listOf(
            """"Version":"([^"]+)","ProductId":"pro","ProductPlatform":"Jar"""".toRegex(),
            """"Version":"([^"]+)"[^}]*"ProductId":"pro"[^}]*"ProductPlatform":"Jar"""".toRegex(),
            """"ProductId":"pro"[^}]*"ProductPlatform":"Jar"[^}]*"Version":"([^"]+)"""".toRegex()
        )

        for ((index, pattern) in patterns.withIndex()) {
            val match = pattern.find(result)
            if (match != null) {
                val version = match.groups[1]?.value ?: ""
                info("Found version using pattern $index: $version")
                return version
            }
        }

        warn("No version found with any pattern")
        return ""

    } catch (e: Exception) {
        error("Error in version check: ${e.message}")
        e.printStackTrace()
        return ""
    }
}

fun getCMD(): Array<String> {
    val keygenPath = getKeygenPath()
    val javaPath = getJavaPath() ?: return arrayOf("Cannot find java! Please put jdk in the same path with keygen.")
    val javaVersion = getJavaVersion(javaPath)

    if (javaVersion < 9) return arrayOf("Java 8 is not supported, please use an older version of the keygen.")

    val cmd = mutableListOf(javaPath)
    if (javaVersion == 16) cmd.add("--illegal-access=permit")
    if (javaVersion >= 17) {
        cmd.addAll(
            listOf(
                "--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.org.objectweb.asm.Opcodes=ALL-UNNAMED"
            )
        )
    }
    cmd.addAll(listOf("-javaagent:$keygenPath", "-noverify", "-jar", getBurpPath()))
    return cmd.toTypedArray()
}

private fun getKeygenPath(): String {
    val keygenFile = File(BurpKeygenApp::class.java.protectionDomain.codeSource.location.path)
    val keygenFilePath = URLDecoder.decode(keygenFile.absolutePath, "UTF-8")

    // If running from classes directory (development), look for the JAR file
    if (keygenFilePath.contains("/build/classes/")) {
        // Look for fat JAR first, then regular JAR
        val projectRoot = keygenFilePath.substringBefore("/build/classes/")
        val fatJar = File("$projectRoot/build/libs/burploader-fat.jar")
        val regularJar = File("$projectRoot/build/libs/burploader.jar")

        return when {
            fatJar.exists() -> fatJar.absolutePath
            regularJar.exists() -> regularJar.absolutePath
            else -> {
                warn("No built JAR found, using classes directory (may not work)")
                keygenFilePath
            }
        }
    }

    // Running from JAR file (production)
    return keygenFilePath
}

private fun getJavaVersion(path: String): Int {
    return try {
        val process = ProcessBuilder(path, "-version").start()
        val output = process.errorStream.bufferedReader().readText()
        process.waitFor()
        val versionLine = output.lines().find { it.contains("version") } ?: return 0
        val versionParts = versionLine.split("\"")[1].split("[._-]".toRegex())
        if (versionParts[0] == "1") versionParts[1].toInt() else versionParts[0].toInt()
    } catch (e: Exception) {
        0
    }
}

private fun getBurpPath(): String {
    return try {
        // First, try to find the installed Burp Suite from DownloadManager
        val installedPath = DownloadManager.getBurpSuiteJarPath()
        if (installedPath != null && Files.exists(installedPath)) {
            return installedPath.toString()
        }

        // Fallback to the current directory for backwards compatibility
        val f = File(BurpKeygenApp::class.java.protectionDomain.codeSource.location.toURI().path)
        val currentDir = if (f.isDirectory) f.path else f.parent
        val dirStream = Files.newDirectoryStream(Paths.get(currentDir), "burpsuite_*.jar")

        dirStream.use { stream ->
            stream.maxByOrNull { it.toFile().lastModified() }?.toString() ?: "burpsuite_jar_not_found.jar"
        }
    } catch (t: Throwable) {
        "burpsuite_jar_not_found.jar"
    }
}

fun getCMDStr(cmd: Array<String>): String = cmd.joinToString(" ") { if (it.contains(" ")) "\"$it\"" else it }

private fun getJavaPath(): String? {
    val keygenFile = File(BurpKeygenApp::class.java.protectionDomain.codeSource.location.path)
    val parent = keygenFile.parent
    val paths = listOf(
        "$parent/bin", "$parent/jre/bin", "$parent/jdk/bin",
        "${System.getProperty("java.home")}/bin"
    )

    for (pathStr in paths) {
        val decodedPath = try { URLDecoder.decode(pathStr, "utf-8") } catch (e: Exception) { pathStr }
        verifyPath("$decodedPath/java")?.let { return it }
    }
    return null
}

private fun verifyPath(path: String): String? {
    val file = File(path)
    if (file.exists() && !file.isDirectory && file.canExecute()) return file.path

    val exeFile = File("$path.exe")
    if (exeFile.exists() && !exeFile.isDirectory && exeFile.canExecute()) return exeFile.path

    return null
}

fun trustAllHosts() {
    val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
    })
    try {
        val sc = SSLContext.getInstance("TLS")
        sc.init(null, trustAllCerts, SecureRandom())
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
        HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}