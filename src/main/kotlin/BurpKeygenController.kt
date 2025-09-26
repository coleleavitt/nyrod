import javafx.application.Platform
import javafx.concurrent.Task
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.*
import javafx.scene.layout.VBox
import javafx.stage.Stage
import java.net.URL
import java.util.*

class BurpKeygenController : Initializable {

    @FXML private lateinit var autoRunCheckBox: CheckBox
    @FXML private lateinit var ignoreUpdateCheckBox: CheckBox
    @FXML private lateinit var themeButton: Button
    @FXML private lateinit var versionLabel: Label
    @FXML private lateinit var commandTextField: TextField
    @FXML private lateinit var runButton: Button
    @FXML private lateinit var licenseTextArea: TextArea
    @FXML private lateinit var activationRequestTextArea: TextArea
    @FXML private lateinit var activationResponseTextArea: TextArea

    @FXML private lateinit var licenseNameField: TextField
    @FXML private lateinit var licenseTypeCombo: ComboBox<LicenseType>
    @FXML private lateinit var expandLicenseButton: Button
    @FXML private lateinit var advancedLicensePanel: VBox
    @FXML private lateinit var companyNameField: TextField
    @FXML private lateinit var emailField: TextField
    @FXML private lateinit var expirationPicker: DatePicker
    @FXML private lateinit var licenseIdField: TextField
    @FXML private lateinit var notesArea: TextArea

    private var latestVersion: String = ""
    private var downloadUrl: String? = null
    private var isAdvancedExpanded = false
    private var isLoadingSettings = false

    override fun initialize(url: URL?, resourceBundle: ResourceBundle?) {
        setupEventHandlers()
        setupLicenseConfiguration()
        loadSettings()
        updateCommand()
        updateLicense()
        checkLatestVersion()
    }

    private fun setupEventHandlers() {
        if (::autoRunCheckBox.isInitialized) {
            autoRunCheckBox.setOnAction { saveSettings() }
        }
        if (::ignoreUpdateCheckBox.isInitialized) {
            ignoreUpdateCheckBox.setOnAction { saveSettings() }
        }

        if (::themeButton.isInitialized) {
            themeButton.setOnAction { cycleTheme() }
        }

        if (::activationRequestTextArea.isInitialized) {
            activationRequestTextArea.textProperty().addListener { _, _, _ -> updateActivationResponse() }
        }

        if (::runButton.isInitialized) {
            runButton.setOnAction { runBurpSuite() }
        }

        if (::versionLabel.isInitialized) {
            versionLabel.setOnMouseClicked {
                downloadUrl?.let { url ->
                    if (latestVersion.isNotEmpty()) {
                        startDownload(latestVersion, url)
                    }
                }
            }
        }

        if (::expandLicenseButton.isInitialized) {
            expandLicenseButton.setOnAction { toggleAdvancedLicense() }
        }

        if (::licenseNameField.isInitialized) {
            licenseNameField.textProperty().addListener { _, _, _ ->
                updateLicense()
                saveLicenseSettings()
            }
        }

        if (::licenseTypeCombo.isInitialized) {
            licenseTypeCombo.setOnAction {
                updateLicense()
                saveLicenseSettings()
            }
        }

        if (::companyNameField.isInitialized) {
            companyNameField.textProperty().addListener { _, _, _ -> saveLicenseSettings() }
        }

        if (::emailField.isInitialized) {
            emailField.textProperty().addListener { _, _, _ -> saveLicenseSettings() }
        }

        if (::licenseIdField.isInitialized) {
            licenseIdField.textProperty().addListener { _, _, _ -> saveLicenseSettings() }
        }

        if (::notesArea.isInitialized) {
            notesArea.textProperty().addListener { _, _, _ -> saveLicenseSettings() }
        }

        if (::expirationPicker.isInitialized) {
            setupDatePickerParsing()
        }
    }

    private fun setupDatePickerParsing() {
        if (::expirationPicker.isInitialized) {
            val converter = object : javafx.util.StringConverter<java.time.LocalDate>() {
                override fun toString(date: java.time.LocalDate?): String {
                    return date?.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) ?: ""
                }

                override fun fromString(string: String?): java.time.LocalDate? {
                    if (string.isNullOrBlank()) return null

                    return try {
                        when {
                            string.matches(Regex("\\d{4}-\\d{1,2}-\\d{1,2}")) ->
                                java.time.LocalDate.parse(string, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                            string.matches(Regex("\\d{4}/\\d{1,2}/\\d{1,2}")) ->
                                java.time.LocalDate.parse(string, java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd"))
                            string.matches(Regex("\\d{1,2}/\\d{1,2}/\\d{4}")) ->
                                java.time.LocalDate.parse(string, java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy"))
                            string.matches(Regex("\\d{1,2}-\\d{1,2}-\\d{4}")) ->
                                java.time.LocalDate.parse(string, java.time.format.DateTimeFormatter.ofPattern("MM-dd-yyyy"))
                            string.matches(Regex("\\d{1,2}\\.\\d{1,2}\\.\\d{4}")) ->
                                java.time.LocalDate.parse(string, java.time.format.DateTimeFormatter.ofPattern("MM.dd.yyyy"))
                            else -> null
                        }
                    } catch (e: Exception) {
                        warn("Failed to parse date: $string - ${e.message}")
                        null
                    }
                }
            }

            expirationPicker.converter = converter

            expirationPicker.valueProperty().addListener { _, _, _ ->
                updateLicense()
                saveLicenseSettings()
            }
        }
    }

    private fun loadSettings() {
        if (::autoRunCheckBox.isInitialized) {
            autoRunCheckBox.isSelected = ConfigManager.getBoolean("auto_run", false)
        }
        if (::ignoreUpdateCheckBox.isInitialized) {
            ignoreUpdateCheckBox.isSelected = ConfigManager.getBoolean("ignore_updates", false)
        }
        updateThemeButtonText()
        loadLicenseSettings()
    }

    private fun saveSettings() {
        if (::autoRunCheckBox.isInitialized) {
            ConfigManager.putBoolean("auto_run", autoRunCheckBox.isSelected)
        }
        if (::ignoreUpdateCheckBox.isInitialized) {
            ConfigManager.putBoolean("ignore_updates", ignoreUpdateCheckBox.isSelected)
        }
    }

    private fun loadLicenseSettings() {
        isLoadingSettings = true
        val settings = ConfigManager.getLicenseSettings()

        if (::licenseNameField.isInitialized) {
            licenseNameField.text = settings["license_name"] ?: "Licensed to John Doe"
        }
        if (::companyNameField.isInitialized) {
            companyNameField.text = settings["company_name"] ?: ""
        }
        if (::emailField.isInitialized) {
            emailField.text = settings["email"] ?: ""
        }
        if (::licenseTypeCombo.isInitialized) {
            val savedType = settings["license_type"] ?: "PROFESSIONAL"
            try {
                licenseTypeCombo.value = LicenseType.valueOf(savedType)
            } catch (e: IllegalArgumentException) {
                licenseTypeCombo.value = LicenseType.PROFESSIONAL
            }
        }
        if (::expirationPicker.isInitialized) {
            val dateStr = settings["expiration_date"]
            if (!dateStr.isNullOrBlank()) {
                try {
                    expirationPicker.value = java.time.LocalDate.parse(dateStr)
                } catch (e: Exception) {
                    warn("Failed to parse saved expiration date: $dateStr")
                }
            }
        }
        if (::licenseIdField.isInitialized) {
            licenseIdField.text = settings["license_id"] ?: ""
        }
        if (::notesArea.isInitialized) {
            notesArea.text = settings["notes"] ?: ""
        }
        isLoadingSettings = false
    }

    private fun saveLicenseSettings() {
        if (isLoadingSettings) return

        if (::licenseNameField.isInitialized && ::companyNameField.isInitialized &&
            ::emailField.isInitialized && ::licenseTypeCombo.isInitialized &&
            ::licenseIdField.isInitialized && ::notesArea.isInitialized) {

            val expirationStr = if (::expirationPicker.isInitialized) {
                expirationPicker.value?.toString()
            } else null

            ConfigManager.saveLicenseSettings(
                licenseName = licenseNameField.text.ifBlank { "Licensed to John Doe" },
                companyName = companyNameField.text,
                email = emailField.text,
                licenseType = (licenseTypeCombo.value ?: LicenseType.PROFESSIONAL).name,
                expirationDate = expirationStr,
                licenseId = licenseIdField.text,
                notes = notesArea.text
            )
        }
    }

    private fun cycleTheme() {
        val currentTheme = ThemeManager.getCurrentTheme()
        val newTheme = when (currentTheme) {
            ThemeMode.AUTO -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.AUTO
        }

        info("Cycling theme from $currentTheme to $newTheme")
        ThemeManager.setTheme(newTheme)
        updateThemeButtonText()

        if (newTheme == ThemeMode.AUTO) {
            ThemeManager.startSystemThemeMonitoring()
        }
    }

    private fun updateThemeButtonText() {
        if (::themeButton.isInitialized) {
            val currentTheme = ThemeManager.getCurrentTheme()
            themeButton.text = ThemeManager.getThemeDescription(currentTheme)
            info("Updated theme button text: ${themeButton.text}")
        }
    }

    private fun updateCommand() {
        try {
            val cmd = getCMD()
            val cmdStr = getCMDStr(cmd)

            if (::commandTextField.isInitialized && ::runButton.isInitialized && ::autoRunCheckBox.isInitialized) {
                if (cmdStr.contains("Cannot find java") || cmdStr.contains("burpsuite_jar_not_found")) {
                    commandTextField.text = cmdStr
                    runButton.isDisable = true
                    autoRunCheckBox.isDisable = cmdStr.contains("burpsuite_jar_not_found")
                } else {
                    commandTextField.text = cmdStr
                    runButton.isDisable = false
                }
            }
        } catch (e: Exception) {
            if (::commandTextField.isInitialized && ::runButton.isInitialized) {
                commandTextField.text = "Error: ${e.message}"
                runButton.isDisable = true
            }
        }
    }

    private fun updateLicense() {
        if (::licenseTextArea.isInitialized && ::licenseNameField.isInitialized && ::licenseTypeCombo.isInitialized) {
            try {
                val licenseName = licenseNameField.text.ifBlank { "Licensed to John Doe" }
                val licenseType = licenseTypeCombo.value ?: LicenseType.PROFESSIONAL
                val expirationDate = if (::expirationPicker.isInitialized) expirationPicker.value?.atStartOfDay() else null
                val customId = if (::licenseIdField.isInitialized && licenseIdField.text.isNotBlank()) licenseIdField.text else null

                val profile = LicenseProfile(
                    licenseName = licenseName,
                    licenseType = licenseType,
                    expirationDate = expirationDate,
                    customId = customId
                )

                val licenseKey = LicenseProfileManager.generateLicenseWithProfile(profile)
                licenseTextArea.text = licenseKey
            } catch (e: Exception) {
                licenseTextArea.text = "Error generating license: ${e.message}"
                error("License generation failed: ${e.message}")
            }
        }
    }

    private fun updateActivationResponse() {
        if (::activationRequestTextArea.isInitialized && ::activationResponseTextArea.isInitialized) {
            val requestText = activationRequestTextArea.text
            if (requestText.isNotBlank()) {
                try {
                    activationResponseTextArea.text = generateActivationForUI(requestText)
                } catch (e: Exception) {
                    activationResponseTextArea.text = "Error generating activation response: ${e.message}"
                }
            } else {
                activationResponseTextArea.text = ""
            }
        }
    }

    private fun startDownload(version: String, downloadUrl: String) {
        info("Starting download for version: $version")

        try {
            val stage = versionLabel.scene.window as? Stage
            if (stage != null) {
                DownloadManager.downloadBurpSuite(version, downloadUrl, stage).thenAccept { success ->
                    if (success) {
                        Platform.runLater {
                            updateCommand()
                            updateVersionDisplay()
                        }
                    }
                }
            } else {
                warn("Could not get stage for download dialog")
                showError("Download Error", "Could not initialize download dialog")
            }
        } catch (e: Exception) {
            error("Failed to start download: ${e.message}")
            showError("Download Failed", "Could not start download: ${e.message}")
        }
    }

    private fun updateVersionDisplay() {
        if (::versionLabel.isInitialized) {
            val installedVersion = DownloadManager.getInstalledVersion()
            if (installedVersion != null) {
                if (installedVersion == latestVersion) {
                    versionLabel.text = "Up to date: v$installedVersion"
                    versionLabel.styleClass.clear()
                    versionLabel.styleClass.addAll("version-label", "success")
                    downloadUrl = null
                } else {
                    versionLabel.text = "Latest: v$latestVersion • Click to download"
                    versionLabel.styleClass.clear()
                    versionLabel.styleClass.addAll("version-label", "clickable")
                }
            }
        }
    }

    private fun checkLatestVersion() {
        info("Starting version check...")

        val task = object : Task<String>() {
            override fun call(): String {
                return getLatestVersion()
            }
        }

        task.setOnSucceeded {
            val version = task.value
            latestVersion = version

            Platform.runLater {
                if (::versionLabel.isInitialized) {
                    if (version.isEmpty()) {
                        versionLabel.text = "Failed to check latest version"
                        versionLabel.styleClass.clear()
                        versionLabel.styleClass.addAll("version-label", "error")
                        warn("Version check failed - empty response")
                    } else {
                        val installedVersion = DownloadManager.getInstalledVersion()
                        downloadUrl = "$DOWNLOAD_URL$version"

                        if (installedVersion != null && installedVersion == version) {
                            versionLabel.text = "Up to date: v$version"
                            versionLabel.styleClass.clear()
                            versionLabel.styleClass.addAll("version-label", "success")
                            downloadUrl = null
                            info("Already on latest version: $version")
                        } else {
                            versionLabel.text = "Latest: v$version • Click to download"
                            versionLabel.styleClass.clear()
                            versionLabel.styleClass.addAll("version-label", "clickable")
                            info("Found new version: $version")
                        }
                    }
                }
            }
        }

        task.setOnFailed {
            Platform.runLater {
                if (::versionLabel.isInitialized) {
                    versionLabel.text = "Version check failed"
                    versionLabel.styleClass.clear()
                    versionLabel.styleClass.addAll("version-label", "error")
                    error("Version check task failed: ${task.exception?.message}")
                }
            }
        }

        Thread(task).start()
    }

    private fun runBurpSuite() {
        try {
            val cmd = getCMD()
            info("Starting Burp Suite with command: ${getCMDStr(cmd)}")

            val processBuilder = ProcessBuilder(*cmd)
            processBuilder.redirectErrorStream(true)
            processBuilder.start()

            info("Burp Suite process started successfully")
            showBurpStartedInfo()

        } catch (e: Exception) {
            error("Failed to start Burp Suite: ${e.message}")
            showError("Failed to start Burp Suite", "Could not execute command: ${e.message}\n\nPlease ensure Burp Suite JAR file is in the same directory as this keygen.")
        }
    }

    private fun showError(title: String, message: String) {
        Platform.runLater {
            val alert = Alert(Alert.AlertType.ERROR)
            alert.title = title
            alert.headerText = null
            alert.contentText = message
            alert.showAndWait()
        }
    }

    private fun showBurpStartedInfo() {
        Platform.runLater {
            val alert = Alert(Alert.AlertType.INFORMATION)
            alert.title = "Burp Suite Started"
            alert.headerText = null
            alert.contentText = "Burp Suite has been launched successfully!"
            alert.showAndWait()
        }
    }

    private fun setupLicenseConfiguration() {
        if (::licenseTypeCombo.isInitialized) {
            licenseTypeCombo.items.clear()
            licenseTypeCombo.items.addAll(LicenseType.entries)
            licenseTypeCombo.value = LicenseType.PROFESSIONAL
        }

        updateLicense()
    }

    private fun toggleAdvancedLicense() {
        if (::advancedLicensePanel.isInitialized && ::expandLicenseButton.isInitialized) {
            isAdvancedExpanded = !isAdvancedExpanded
            advancedLicensePanel.isVisible = isAdvancedExpanded
            advancedLicensePanel.isManaged = isAdvancedExpanded

            expandLicenseButton.text = if (isAdvancedExpanded) "Basic" else "Advanced"
            info("Advanced license panel ${if (isAdvancedExpanded) "expanded" else "collapsed"}")
        }
    }
    fun shouldAutoRun(): Boolean = if (::autoRunCheckBox.isInitialized) autoRunCheckBox.isSelected else false
    fun shouldIgnoreUpdate(): Boolean = if (::ignoreUpdateCheckBox.isInitialized) ignoreUpdateCheckBox.isSelected else false
    fun getCurrentCommand(): Array<String> = getCMD()

    companion object {
        private const val DOWNLOAD_URL = "https://portswigger-cdn.net/burp/releases/download?product=pro&type=Jar&version="
    }
}