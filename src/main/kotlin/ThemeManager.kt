import javafx.application.Platform
import javafx.scene.Scene
import java.io.File

enum class ThemeMode {
    AUTO,   // Follow system theme
    DARK,   // Force dark theme
    LIGHT   // Force light theme
}

object ThemeManager {
    private var currentScene: Scene? = null
    private var currentTheme: ThemeMode = ThemeMode.AUTO

    // System theme detection
    private fun detectSystemTheme(): Boolean {
        return try {
            // Method 1: Check GTK theme (Linux/GNOME)
            val gtkTheme = detectGTKTheme()
            if (gtkTheme != null) return gtkTheme

            // Method 2: Check environment variables
            val envTheme = detectEnvironmentTheme()
            if (envTheme != null) return envTheme

            // Method 3: Check desktop environment preferences
            val deTheme = detectDesktopEnvironmentTheme()
            if (deTheme != null) return deTheme

            // Method 4: Check for common dark theme indicators in system properties
            val systemProps = System.getProperties()
            val darkKeywords = listOf("dark", "night", "black")
            for ((key, value) in systemProps) {
                val keyStr = key.toString().lowercase()
                val valueStr = value.toString().lowercase()
                if ((keyStr.contains("theme") || keyStr.contains("style")) &&
                    darkKeywords.any { valueStr.contains(it) }) {
                    info("Dark theme detected in system property: $key=$value")
                    return true
                }
            }

            // Default to dark theme if detection fails
            info("Could not detect system theme, defaulting to dark")
            true
        } catch (e: Exception) {
            warn("Error detecting system theme: ${e.message}")
            true // Default to dark
        }
    }

    private fun detectGTKTheme(): Boolean? {
        return try {
            // Check gsettings for GNOME
            val gnomeResult = runCommand("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme")
            if (gnomeResult != null) {
                val isDark = gnomeResult.lowercase().contains("dark") ||
                            gnomeResult.lowercase().contains("adwaita-dark")
                info("GNOME theme detected: $gnomeResult (dark: $isDark)")
                return isDark
            }

            // Check the GTK settings file
            val homeDir = System.getProperty("user.home")
            val gtkConfigFiles = listOf(
                "$homeDir/.config/gtk-3.0/settings.ini",
                "$homeDir/.gtkrc-2.0"
            )

            for (configFile in gtkConfigFiles) {
                val file = File(configFile)
                if (file.exists()) {
                    val content = file.readText()
                    if (content.contains("gtk-theme-name")) {
                        val isDark = content.lowercase().contains("dark")
                        info("GTK config theme detected from $configFile (dark: $isDark)")
                        return isDark
                    }
                }
            }

            null
        } catch (e: Exception) {
            info("GTK theme detection failed: ${e.message}")
            null
        }
    }

    private fun detectEnvironmentTheme(): Boolean? {
        return try {
            val themeVars = listOf(
                "GTK_THEME", "QT_STYLE_OVERRIDE", "DESKTOP_SESSION"
            )

            for (envVar in themeVars) {
                val value = System.getenv(envVar)?.lowercase()
                if (!value.isNullOrBlank()) {
                    val isDark = value.contains("dark")
                    if (isDark || value.contains("light")) {
                        info("Environment theme detected from $envVar: $value (dark: $isDark)")
                        return isDark
                    }
                }
            }
            null
        } catch (e: Exception) {
            info("Environment theme detection failed: ${e.message}")
            null
        }
    }

    private fun detectDesktopEnvironmentTheme(): Boolean? {
        return try {
            val session = System.getenv("DESKTOP_SESSION")?.lowercase()
            val de = System.getenv("XDG_CURRENT_DESKTOP")?.lowercase()

            when {
                // KDE Plasma
                session?.contains("plasma") == true || de?.contains("kde") == true -> {
                    val kdeResult = runCommand("kreadconfig5", "--group", "General", "--key", "ColorScheme")
                    val isDark = kdeResult?.lowercase()?.contains("dark") ?: false
                    info("KDE theme detected: $kdeResult (dark: $isDark)")
                    isDark
                }
                // XFCE
                de?.contains("xfce") == true -> {
                    val xfceResult = runCommand("xfconf-query", "-c", "xsettings", "-p", "/Net/ThemeName")
                    val isDark = xfceResult?.lowercase()?.contains("dark") ?: false
                    info("XFCE theme detected: $xfceResult (dark: $isDark)")
                    isDark
                }
                else -> null
            }
        } catch (e: Exception) {
            info("Desktop environment theme detection failed: ${e.message}")
            null
        }
    }


    private fun runCommand(vararg command: String): String? {
        return try {
            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()

            val result = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()

            if (process.exitValue() == 0 && result.isNotBlank()) {
                result
            } else null
        } catch (e: Exception) {
            null
        }
    }

    // Theme management functions
    fun initialize(scene: Scene) {
        currentScene = scene
        loadSavedTheme()
        applyTheme()
    }

    private fun loadSavedTheme() {
        val savedTheme = ConfigManager.getString("theme_mode", ThemeMode.AUTO.name)
        currentTheme = try {
            ThemeMode.valueOf(savedTheme)
        } catch (e: Exception) {
            ThemeMode.AUTO
        }
        info("Loaded saved theme: $currentTheme")
    }

    fun setTheme(theme: ThemeMode) {
        currentTheme = theme
        ConfigManager.putString("theme_mode", theme.name)
        info("Theme changed to: $theme")
        applyTheme()
    }

    fun getCurrentTheme(): ThemeMode = currentTheme

    fun getEffectiveTheme(): String {
        return when (currentTheme) {
            ThemeMode.DARK -> "dark"
            ThemeMode.LIGHT -> "light"
            ThemeMode.AUTO -> if (detectSystemTheme()) "dark" else "light"
        }
    }

    private fun applyTheme() {
        val scene = currentScene ?: return

        Platform.runLater {
            try {
                // Clear existing stylesheets
                scene.stylesheets.clear()

                val effectiveTheme = getEffectiveTheme()
                info("Applying theme: $effectiveTheme")

                // Load appropriate CSS file
                val cssFile = when (effectiveTheme) {
                    "dark" -> "/themes/dark-theme.css"
                    "light" -> "/themes/light-theme.css"
                    else -> "/themes/dark-theme.css" // fallback
                }

                val cssResource = ThemeManager::class.java.getResource(cssFile)
                if (cssResource != null) {
                    scene.stylesheets.add(cssResource.toExternalForm())
                    info("Successfully applied theme: $cssFile")
                } else {
                    warn("CSS file not found: $cssFile, falling back to default")
                    // Fallback to the main CSS file
                    val fallbackCss = ThemeManager::class.java.getResource("/burp-keygen.css")
                    if (fallbackCss != null) {
                        scene.stylesheets.add(fallbackCss.toExternalForm())
                    }
                }

                // Set system property for other JavaFX components
                System.setProperty("javafx.userAgentStylesheetUrl", effectiveTheme)

            } catch (e: Exception) {
                error("Error applying theme: ${e.message}")
            }
        }
    }

    // Monitor system theme changes (for auto mode)
    fun startSystemThemeMonitoring() {
        if (currentTheme == ThemeMode.AUTO) {
            Thread {
                var lastDetectedTheme = detectSystemTheme()

                while (true) {
                    try {
                        Thread.sleep(5000) // Check every 5 seconds

                        if (currentTheme == ThemeMode.AUTO) {
                            val newTheme = detectSystemTheme()
                            if (newTheme != lastDetectedTheme) {
                                info("System theme changed from $lastDetectedTheme to $newTheme")
                                lastDetectedTheme = newTheme
                                applyTheme()
                            }
                        } else {
                            break // Stop monitoring if not in auto mode
                        }
                    } catch (e: InterruptedException) {
                        break
                    } catch (e: Exception) {
                        warn("Error in theme monitoring: ${e.message}")
                    }
                }
            }.apply {
                isDaemon = true
                name = "ThemeMonitor"
            }.start()
        }
    }

    // Get available themes
    fun getAvailableThemes(): List<ThemeMode> {
        return ThemeMode.entries
    }

    // Theme description for UI
    fun getThemeDescription(theme: ThemeMode): String {
        return when (theme) {
            ThemeMode.AUTO -> "Auto (Follow System)"
            ThemeMode.DARK -> "Dark Theme"
            ThemeMode.LIGHT -> "Light Theme"
        }
    }
}