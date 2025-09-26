import ch.bailu.gtk.gio.ApplicationFlags
import ch.bailu.gtk.glib.Glib
import ch.bailu.gtk.gtk.*
import ch.bailu.gtk.type.Str
import ch.bailu.gtk.type.Strs
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.concurrent.thread
import kotlin.system.exitProcess

class GtkBurpKeygenApp {

    private lateinit var app: Application
    private lateinit var window: ApplicationWindow
    private lateinit var outputTextView: TextView

    // UI Components
    private lateinit var licenseNameEntry: Entry
    private lateinit var licenseTypeCombo: DropDown
    private lateinit var expirationCalendar: Calendar
    private lateinit var expirationEntry: Entry
    private lateinit var customIdEntry: Entry
    private lateinit var organizationEntry: Entry
    private lateinit var emailEntry: Entry

    // License key and activation components
    private lateinit var licenseKeyTextView: TextView
    private lateinit var activationRequestTextView: TextView
    private lateinit var activationResponseTextView: TextView

    // Launch configuration components
    private lateinit var launchCommandTextView: TextView

    // Download button component
    private lateinit var downloadButton: Button

    fun run(args: Array<String>) {
        // Set up SSL trust before any network calls
        trustAllHosts()

        // Create GTK application
        app = Application(Str("com.burploader.keygen"), ApplicationFlags.FLAGS_NONE)

        // Connect activate signal
        app.onActivate { createUI() }

        // Run application
        val result = app.run(args.size, Strs(args))
        exitProcess(result)
    }

    private fun createUI() {
        // Create main window
        window = ApplicationWindow(app)
        window.setTitle(Str("Burp Suite Pro Loader & Keygen"))
        window.setDefaultSize(1000, 700)

        // Create main vertical box
        val mainBox = Box(Orientation.VERTICAL, 12)
        mainBox.setMarginTop(20)
        mainBox.setMarginBottom(20)
        mainBox.setMarginStart(20)
        mainBox.setMarginEnd(20)

        // Add title
        val title = Label(Str("Burp Suite Pro Loader & Keygen"))
        mainBox.append(title)

        // Create license profile section
        val profileFrame = Frame(Str("License Profile"))
        val profileBox = Box(Orientation.VERTICAL, 8)
        profileBox.setMarginTop(12)
        profileBox.setMarginBottom(12)
        profileBox.setMarginStart(12)
        profileBox.setMarginEnd(12)

        // License name
        val nameBox = Box(Orientation.HORIZONTAL, 8)
        val nameLabel = Label(Str("License Name:"))
        nameLabel.setHalign(Align.START)
        nameLabel.setSizeRequest(150, -1)
        licenseNameEntry = Entry()
        licenseNameEntry.getBuffer().setText(Str("Licensed to John Doe"), -1)
        licenseNameEntry.setHexpand(true)
        // Auto-generate license when name changes - use Entry's buffer events
        licenseNameEntry.getBuffer().onInsertedText { _, _, _ -> generateLicense() }
        licenseNameEntry.getBuffer().onDeletedText { _, _ -> generateLicense() }
        nameBox.append(nameLabel)
        nameBox.append(licenseNameEntry)
        profileBox.append(nameBox)

        // License type
        val typeBox = Box(Orientation.HORIZONTAL, 8)
        val typeLabel = Label(Str("License Type:"))
        typeLabel.setHalign(Align.START)
        typeLabel.setSizeRequest(150, -1)
        // Create StringList with license types
        val licenseTypes = StringList(null as ch.bailu.gtk.type.Strs?)
        licenseTypes.append(Str("Professional"))
        licenseTypes.append(Str("Enterprise"))
        licenseTypes.append(Str("Trial"))
        licenseTypes.append(Str("Educational"))
        licenseTypeCombo = DropDown(licenseTypes.asListModel(), null)
        licenseTypeCombo.setSelected(0)
        licenseTypeCombo.setHexpand(true)
        // Note: DropDown doesn't have simple change callbacks like ComboBoxText
        // License will be generated when user interaction triggers it
        typeBox.append(typeLabel)
        typeBox.append(licenseTypeCombo)
        profileBox.append(typeBox)

        // Organization
        val orgBox = Box(Orientation.HORIZONTAL, 8)
        val orgLabel = Label(Str("Organization:"))
        orgLabel.setHalign(Align.START)
        orgLabel.setSizeRequest(150, -1)
        organizationEntry = Entry()
        organizationEntry.getBuffer().setText(Str("Security Research Lab"), -1)
        organizationEntry.setHexpand(true)
        // Auto-generate license when organization changes
        organizationEntry.getBuffer().onInsertedText { _, _, _ -> generateLicense() }
        organizationEntry.getBuffer().onDeletedText { _, _ -> generateLicense() }
        orgBox.append(orgLabel)
        orgBox.append(organizationEntry)
        profileBox.append(orgBox)

        // Email
        val emailBox = Box(Orientation.HORIZONTAL, 8)
        val emailLabel = Label(Str("Email:"))
        emailLabel.setHalign(Align.START)
        emailLabel.setSizeRequest(150, -1)
        emailEntry = Entry()
        emailEntry.getBuffer().setText(Str("researcher@example.com"), -1)
        emailEntry.setHexpand(true)
        // Auto-generate license when email changes
        emailEntry.getBuffer().onInsertedText { _, _, _ -> generateLicense() }
        emailEntry.getBuffer().onDeletedText { _, _ -> generateLicense() }
        emailBox.append(emailLabel)
        emailBox.append(emailEntry)
        profileBox.append(emailBox)

        // Custom ID
        val idBox = Box(Orientation.HORIZONTAL, 8)
        val idLabel = Label(Str("Custom ID:"))
        idLabel.setHalign(Align.START)
        idLabel.setSizeRequest(150, -1)
        customIdEntry = Entry()
        customIdEntry.getBuffer().setText(Str("Auto-generate"), -1)
        customIdEntry.setHexpand(true)
        // Auto-generate license when custom ID changes
        customIdEntry.getBuffer().onInsertedText { _, _, _ -> generateLicense() }
        customIdEntry.getBuffer().onDeletedText { _, _ -> generateLicense() }
        idBox.append(idLabel)
        idBox.append(customIdEntry)
        profileBox.append(idBox)

        // Expiration with calendar picker button
        val expirationBox = Box(Orientation.HORIZONTAL, 8)
        val expirationLabel = Label(Str("Expiration:"))
        expirationLabel.setHalign(Align.START)
        expirationLabel.setSizeRequest(150, -1)

        // Create a container for entry + button (like other fields)
        val expirationContainer = Box(Orientation.HORIZONTAL, 4)
        expirationEntry = Entry()
        expirationEntry.getBuffer().setText(Str("Never expires"), -1)
        expirationEntry.setHexpand(true)
        expirationEntry.setPlaceholderText(Str("YYYY-MM-DD or 'Never expires'"))
        // Auto-generate license when expiration changes
        expirationEntry.getBuffer().onInsertedText { _, _, _ -> generateLicense() }
        expirationEntry.getBuffer().onDeletedText { _, _ -> generateLicense() }

        // Create hidden calendar widget for the popover
        expirationCalendar = Calendar()

        // Calendar picker button with icon
        val calendarButton = Button()
        calendarButton.setIconName(Str("x-office-calendar"))
        calendarButton.setTooltipText(Str("Choose date"))
        calendarButton.onClicked { showCalendarPicker() }

        expirationContainer.append(expirationEntry)
        expirationContainer.append(calendarButton)

        expirationBox.append(expirationLabel)
        expirationBox.append(expirationContainer)
        profileBox.append(expirationBox)

        profileFrame.setChild(profileBox)
        mainBox.append(profileFrame)

        // Add Generate License button
        val generateButtonBox = Box(Orientation.HORIZONTAL, 8)
        generateButtonBox.setHalign(Align.CENTER)
        generateButtonBox.setMarginTop(8)
        generateButtonBox.setMarginBottom(8)

        val generateButton = Button()
        generateButton.setLabel(Str("Generate License"))
        generateButton.addCssClass(Str("suggested-action"))
        generateButton.onClicked { generateLicense() }

        generateButtonBox.append(generateButton)
        mainBox.append(generateButtonBox)

        // Create launch configuration section
        val launchConfigFrame = Frame(Str("Launch Configuration"))
        val launchConfigBox = Box(Orientation.VERTICAL, 8)
        launchConfigBox.setMarginTop(12)
        launchConfigBox.setMarginBottom(12)
        launchConfigBox.setMarginStart(12)
        launchConfigBox.setMarginEnd(12)

        // Command label and description
        val commandLabel = Label(Str("Launch Command:"))
        commandLabel.setHalign(Align.START)
        launchConfigBox.append(commandLabel)

        val commandDescription = Label(Str("This is the command that will be executed when launching Burp Suite:"))
        commandDescription.setHalign(Align.START)
        commandDescription.addCssClass(Str("dim-label"))
        commandDescription.setMarginBottom(8)
        launchConfigBox.append(commandDescription)

        // Command text view in a frame
        val commandFrame = Frame(null as Str?)
        val commandScrolled = ScrolledWindow()
        commandScrolled.setPolicy(PolicyType.AUTOMATIC, PolicyType.AUTOMATIC)
        commandScrolled.setMinContentHeight(120)
        commandScrolled.setHexpand(true)

        launchCommandTextView = TextView()
        launchCommandTextView.setMonospace(true)
        launchCommandTextView.setWrapMode(WrapMode.WORD_CHAR)
        launchCommandTextView.setTopMargin(12)
        launchCommandTextView.setBottomMargin(12)
        launchCommandTextView.setLeftMargin(12)
        launchCommandTextView.setRightMargin(12)

        // Set initial command
        updateLaunchCommand()

        commandScrolled.setChild(launchCommandTextView)
        commandFrame.setChild(commandScrolled)
        launchConfigBox.append(commandFrame)

        launchConfigFrame.setChild(launchConfigBox)
        mainBox.append(launchConfigFrame)

        // Create action buttons
        val buttonBox = Box(Orientation.HORIZONTAL, 12)
        buttonBox.setHalign(Align.CENTER)

        downloadButton = Button()
        downloadButton.setLabel(Str("Checking..."))
        downloadButton.onClicked { handleDownloadAction() }

        val launchButton = Button()
        launchButton.setLabel(Str("Launch Burp Suite"))
        launchButton.onClicked { launchBurpSuite() }

        buttonBox.append(downloadButton)
        buttonBox.append(launchButton)
        mainBox.append(buttonBox)

        // Create tabbed interface for different areas
        val notebook = Notebook()
        notebook.setVexpand(true)

        // Tab 1: Generated License Key
        val licenseKeyTab = createLicenseKeyTab()
        notebook.appendPage(licenseKeyTab, Label(Str("License Key")))

        // Tab 2: Activation Request/Response
        val activationTab = createActivationTab()
        notebook.appendPage(activationTab, Label(Str("Activation")))

        // Tab 3: Console Output
        val outputTab = createOutputTab()
        notebook.appendPage(outputTab, Label(Str("Console")))

        mainBox.append(notebook)

        // Set main content
        window.setChild(mainBox)

        // Load existing config values
        loadConfigValues()

        // Generate initial license with default values
        generateLicense()

        // Check Burp Suite download status
        checkDownloadStatus()

        // Handle auto-run logic
        handleAutoRun()

        // Show window
        window.present()
    }

    private fun loadConfigValues() {
        val configFile = File(".config.ini")
        if (!configFile.exists()) {
            appendOutput("No existing config found, using defaults")
            return
        }

        try {
            val configProperties = mutableMapOf<String, String>()
            configFile.readLines().forEach { line ->
                val trimmed = line.trim()
                if (!trimmed.startsWith("#") && trimmed.contains("=")) {
                    val (key, value) = trimmed.split("=", limit = 2)
                    configProperties[key.trim()] = value.trim()
                }
            }

            // Update UI fields with loaded values
            configProperties["license_name"]?.let { name ->
                val nameStr = Str(name)
                licenseNameEntry.getBuffer().setText(nameStr, -1)
                nameStr.destroy()
            }

            configProperties["company_name"]?.let { company ->
                val companyStr = Str(company)
                organizationEntry.getBuffer().setText(companyStr, -1)
                companyStr.destroy()
            }

            configProperties["email"]?.let { email ->
                val emailStr = Str(email)
                emailEntry.getBuffer().setText(emailStr, -1)
                emailStr.destroy()
            }

            configProperties["license_type"]?.let { type ->
                when (type.uppercase()) {
                    "PROFESSIONAL" -> licenseTypeCombo.setSelected(0)
                    "ENTERPRISE" -> licenseTypeCombo.setSelected(1)
                    "TRIAL" -> licenseTypeCombo.setSelected(2)
                    "EDUCATIONAL" -> licenseTypeCombo.setSelected(3)
                    else -> licenseTypeCombo.setSelected(0)
                }
            }

            configProperties["expiration_date"]?.let { expiration ->
                if (expiration.isNotBlank() && expiration.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) {
                    try {
                        val date = LocalDate.parse(expiration, DateTimeFormatter.ISO_LOCAL_DATE)
                        // Set both the calendar and entry field
                        expirationCalendar.setYear(date.year)
                        expirationCalendar.setMonth(date.monthValue - 1) // GTK months are 0-based
                        expirationCalendar.setDay(date.dayOfMonth)

                        val dateStr = Str(expiration)
                        expirationEntry.getBuffer().setText(dateStr, -1)
                        dateStr.destroy()
                    } catch (e: Exception) {
                        appendOutput("Invalid date format in config: $expiration")
                    }
                } else {
                    // Handle "Never expires" or other non-date values
                    val neverStr = Str("Never expires")
                    expirationEntry.getBuffer().setText(neverStr, -1)
                    neverStr.destroy()
                }
            }

            configProperties["license_id"]?.let { id ->
                if (id.isNotBlank()) {
                    val idStr = Str(id)
                    customIdEntry.getBuffer().setText(idStr, -1)
                    idStr.destroy()
                } else {
                    val autoStr = Str("Auto-generate")
                    customIdEntry.getBuffer().setText(autoStr, -1)
                    autoStr.destroy()
                }
            }

            appendOutput("✅ Loaded existing configuration:")
            appendOutput("  ├─ Name: ${configProperties["license_name"] ?: "Default"}")
            appendOutput("  ├─ Organization: ${configProperties["company_name"] ?: "Default"}")
            appendOutput("  ├─ Email: ${configProperties["email"] ?: "Default"}")
            appendOutput("  ├─ Type: ${configProperties["license_type"] ?: "PROFESSIONAL"}")
            appendOutput("  └─ Expiration: ${configProperties["expiration_date"] ?: "Never expires"}")

        } catch (e: Exception) {
            appendOutput("Error loading config: ${e.message}")
            appendOutput("Using default values instead")
        }
    }

    private fun generateLicense() {
        appendOutput("=== Generating Burp Suite License ===")

        // Read all profile information
        val name = licenseNameEntry.getBuffer().getText().toString()
        val selectedIndex = licenseTypeCombo.getSelected()
        val type = when (selectedIndex) {
            0 -> "Professional"
            1 -> "Enterprise"
            2 -> "Trial"
            3 -> "Educational"
            else -> "Professional"
        }
        val organization = organizationEntry.getBuffer().getText().toString()
        val email = emailEntry.getBuffer().getText().toString()
        val customId = customIdEntry.getBuffer().getText().toString()

        val expiration = expirationEntry.getBuffer().getText().toString()

        // Display profile information
        appendOutput("License Details:")
        appendOutput("  ├─ Name: $name")
        appendOutput("  ├─ Type: $type")
        appendOutput("  ├─ Organization: $organization")
        appendOutput("  ├─ Email: $email")
        appendOutput("  ├─ Custom ID: ${if (customId == "Auto-generate") "Auto-generated" else customId}")
        appendOutput("  └─ Expiration: $expiration")

        try {
            val licenseName = "$name ($organization) <$email>"

            appendOutput("Generating cryptographic signatures...")
            val license = generateLicense(licenseName)
            val configPath = writeConfigWithProfile(
                license = license,
                name = name,
                organization = organization,
                email = email,
                licenseType = type,
                expiration = expiration,
                customId = if (customId == "Auto-generate") "Auto-generate" else customId
            )

            // Display license in the license key tab
            val licenseStr = Str(license)
            licenseKeyTextView.getBuffer().setText(licenseStr, -1)
            licenseStr.destroy()

            appendOutput("✅ License generated successfully!")
            appendOutput("Config written to: $configPath")
            appendOutput("Ready to launch Burp Suite!")

        } catch (e: Exception) {
            appendOutput("Error generating license: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun downloadBurpSuite() {
        appendOutput("=== Starting Burp Suite Download ===")
        appendOutput("Connecting to PortSwigger CDN...")

        // Create and show a simple progress dialog
        val progressDialog = Window()
        progressDialog.setTitle(Str("Downloading Burp Suite"))
        progressDialog.setTransientFor(window)
        progressDialog.setModal(true)
        progressDialog.setDefaultSize(400, 150)
        val vbox = Box(Orientation.VERTICAL, 16)
        vbox.setMarginTop(20)
        vbox.setMarginBottom(20)
        vbox.setMarginStart(20)
        vbox.setMarginEnd(20)

        val statusLabel = Label(Str("Starting download..."))
        statusLabel.setHalign(Align.START)
        vbox.append(statusLabel)

        val progressBar = ProgressBar()
        progressBar.setHexpand(true)
        vbox.append(progressBar)

        val detailsLabel = Label(Str("Preparing..."))
        detailsLabel.setHalign(Align.START)
        detailsLabel.addCssClass(Str("dim-label"))
        vbox.append(detailsLabel)

        // Add cancel button to the dialog
        val cancelButton = Button()
        cancelButton.setLabel(Str("Cancel"))
        cancelButton.setMarginTop(12)
        vbox.append(cancelButton)

        progressDialog.setChild(vbox)
        progressDialog.present()

        // Create downloader instance outside the thread so we can cancel it
        val downloader = BurpSuiteDownloader()

        // Set up cancel button action
        cancelButton.onClicked {
            downloader.cancel()
            progressDialog.close()
            appendOutput("Download cancelled by user")
        }

        // Start download in background thread to prevent UI freezing
        thread {
            try {
                val result = downloader.downloadBurpSuite { progress ->
                    // Schedule UI updates on the main thread using Glib.idleAdd
                    Glib.idleAdd({ _, _ ->
                        try {
                            // Update progress dialog
                            progressBar.setFraction(progress.percentage / 100.0)

                            val statusText = "${progress.percentage}% complete"
                            val statusStr = Str(statusText)
                            statusLabel.setText(statusStr)
                            statusStr.destroy()

                            val downloaded = formatBytes(progress.bytesDownloaded)
                            val total = formatBytes(progress.totalBytes)
                            val detailsText = "$downloaded / $total @ ${progress.speed}"
                            val detailsStr = Str(detailsText)
                            detailsLabel.setText(detailsStr)
                            detailsStr.destroy()

                            // Also log to console
                            appendOutput("$downloaded / $total (${progress.percentage}%) @ ${progress.speed}")

                            if (progress.percentage == 100) {
                                appendOutput("Download completed!")
                            }
                        } catch (e: Exception) {
                            // Ignore UI update errors if dialog was closed
                        }

                        false // Don't repeat this idle callback
                    }, null)
                }

                // Schedule final UI updates on the main thread
                Glib.idleAdd({ _, _ ->
                    try {
                        // Close progress dialog
                        progressDialog.close()

                        if (result.success) {
                            appendOutput("✅ Burp Suite downloaded successfully!")
                            appendOutput("Location: ${result.filePath}")
                            appendOutput("Ready to launch!")

                            // Update button status after successful download
                            checkDownloadStatus()
                        } else {
                            appendOutput("Download failed: ${result.error}")
                        }
                    } catch (e: Exception) {
                        // Ignore errors if dialog was already closed
                    }

                    false // Don't repeat this idle callback
                }, null)

            } catch (e: Exception) {
                // Schedule error handling on the main thread
                Glib.idleAdd({ _, _ ->
                    try {
                        progressDialog.close()
                        appendOutput("Download error: ${e.message}")
                        e.printStackTrace()
                    } catch (ignored: Exception) {
                        // Ignore errors if dialog was already closed
                    }
                    false // Don't repeat this idle callback
                }, null)
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0

        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }

        return "%.1f %s".format(size, units[unitIndex])
    }

    private fun createProgressBar(percentage: Int): String {
        val barLength = 30
        val filledLength = (percentage * barLength) / 100
        val emptyLength = barLength - filledLength

        return "[" + "█".repeat(filledLength) + "░".repeat(emptyLength) + "] $percentage%"
    }

    private fun launchBurpSuite() {
        appendOutput("=== Launching Burp Suite Pro ===")

        try {
            val burpJar = File(System.getProperty("user.home"), ".local/share/BurpSuite/burpsuite_pro_v2025.9.3.jar")
            val loaderJar = File("build/libs/burploader-fat.jar")

            // Pre-flight checks
            appendOutput("Pre-flight checks...")

            if (!burpJar.exists()) {
                appendOutput("Burp Suite JAR not found at: ${burpJar.absolutePath}")
                appendOutput("Please download Burp Suite first using the Download button")
                return
            } else {
                val sizeStr = formatBytes(burpJar.length())
                appendOutput("✅ Burp Suite JAR found ($sizeStr)")
            }

            if (!loaderJar.exists()) {
                appendOutput("Loader JAR not found at: ${loaderJar.absolutePath}")
                appendOutput("Please build the project first with: ./gradlew fatJar")
                return
            } else {
                val sizeStr = formatBytes(loaderJar.length())
                appendOutput("✅ Agent loader found ($sizeStr)")
            }

            // Update command textbox with verified paths
            updateLaunchCommand()

            // Launch configuration
            appendOutput("Configuring launch parameters...")
            val cmd = arrayOf(
                "java",
                "--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.org.objectweb.asm.Opcodes=ALL-UNNAMED",
                "--add-opens=java.base/sun.security.util=ALL-UNNAMED",
                "-javaagent:${loaderJar.absolutePath}",
                "-jar", burpJar.absolutePath
            )

            appendOutput("Starting Burp Suite with licensed agent...")
            ProcessBuilder(*cmd).start()
            appendOutput("✅ Burp Suite Pro launched successfully!")
            appendOutput("Process started in background")
            appendOutput("License injected via Java agent")
            appendOutput("Enjoy your licensed Burp Suite Pro!")

        } catch (e: Exception) {
            appendOutput("Launch error: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun handleAutoRun() {
        val configFile = File(".config.ini")
        if (!configFile.exists()) return

        val config = configFile.readText()
        if (config.contains("autoRun=true", ignoreCase = true)) {
            appendOutput("Auto-run mode detected...")
            launchBurpSuite()
        }
    }

    private fun createLicenseKeyTab(): Widget {
        val vbox = Box(Orientation.VERTICAL, 20)
        vbox.setMarginTop(20)
        vbox.setMarginBottom(20)
        vbox.setMarginStart(20)
        vbox.setMarginEnd(20)

        // License key section
        val licenseLabel = Label(Str("Generated License Key"))
        licenseLabel.setHalign(Align.START)
        licenseLabel.addCssClass(Str("heading"))
        vbox.append(licenseLabel)

        val helpLabel = Label(Str("This license key will be automatically saved and used by the agent:"))
        helpLabel.setHalign(Align.START)
        helpLabel.addCssClass(Str("dim-label"))
        helpLabel.setMarginBottom(8)
        vbox.append(helpLabel)

        val licenseFrame = Frame(null as Str?)
        val scrolledWindow = ScrolledWindow()
        scrolledWindow.setPolicy(PolicyType.AUTOMATIC, PolicyType.AUTOMATIC)
        scrolledWindow.setVexpand(true)
        scrolledWindow.setHexpand(true)

        licenseKeyTextView = TextView()
        licenseKeyTextView.setMonospace(true)
        licenseKeyTextView.setWrapMode(WrapMode.WORD_CHAR)
        licenseKeyTextView.setTopMargin(12)
        licenseKeyTextView.setBottomMargin(12)
        licenseKeyTextView.setLeftMargin(12)
        licenseKeyTextView.setRightMargin(12)
        licenseKeyTextView.getBuffer().setText(Str("Click 'Generate License' to create a license key..."), -1)

        scrolledWindow.setChild(licenseKeyTextView)
        licenseFrame.setChild(scrolledWindow)
        vbox.append(licenseFrame)

        return vbox
    }

    private fun createActivationTab(): Widget {
        val vbox = Box(Orientation.VERTICAL, 20)
        vbox.setMarginTop(20)
        vbox.setMarginBottom(20)
        vbox.setMarginStart(20)
        vbox.setMarginEnd(20)

        // Activation Request section
        val requestLabel = Label(Str("Activation Request"))
        requestLabel.setHalign(Align.START)
        requestLabel.addCssClass(Str("heading"))
        vbox.append(requestLabel)

        val requestHelpLabel = Label(Str("Paste the activation request from Burp Suite below:"))
        requestHelpLabel.setHalign(Align.START)
        requestHelpLabel.addCssClass(Str("dim-label"))
        requestHelpLabel.setMarginBottom(8)
        vbox.append(requestHelpLabel)

        val requestFrame = Frame(null as Str?)
        val requestScrolled = ScrolledWindow()
        requestScrolled.setPolicy(PolicyType.AUTOMATIC, PolicyType.AUTOMATIC)
        requestScrolled.setMinContentHeight(180)
        requestScrolled.setHexpand(true)

        activationRequestTextView = TextView()
        activationRequestTextView.setMonospace(true)
        activationRequestTextView.setWrapMode(WrapMode.WORD_CHAR)
        activationRequestTextView.setTopMargin(12)
        activationRequestTextView.setBottomMargin(12)
        activationRequestTextView.setLeftMargin(12)
        activationRequestTextView.setRightMargin(12)
        activationRequestTextView.getBuffer().setText(Str("Paste activation request from Burp Suite here..."), -1)

        // Watch for changes in activation request
        activationRequestTextView.getBuffer().onChanged { updateActivationResponse() }

        requestScrolled.setChild(activationRequestTextView)
        requestFrame.setChild(requestScrolled)
        vbox.append(requestFrame)

        // Separator with more space
        val separator = Separator(Orientation.HORIZONTAL)
        separator.setMarginTop(20)
        separator.setMarginBottom(20)
        vbox.append(separator)

        // Activation Response section
        val responseLabel = Label(Str("Activation Response"))
        responseLabel.setHalign(Align.START)
        responseLabel.addCssClass(Str("heading"))
        vbox.append(responseLabel)

        val responseHelpLabel = Label(Str("Copy this response and paste it back into Burp Suite:"))
        responseHelpLabel.setHalign(Align.START)
        responseHelpLabel.addCssClass(Str("dim-label"))
        responseHelpLabel.setMarginBottom(8)
        vbox.append(responseHelpLabel)

        val responseFrame = Frame(null as Str?)
        val responseScrolled = ScrolledWindow()
        responseScrolled.setPolicy(PolicyType.AUTOMATIC, PolicyType.AUTOMATIC)
        responseScrolled.setMinContentHeight(180)
        responseScrolled.setHexpand(true)

        activationResponseTextView = TextView()
        activationResponseTextView.setMonospace(true)
        activationResponseTextView.setWrapMode(WrapMode.WORD_CHAR)
        activationResponseTextView.setEditable(false)
        activationResponseTextView.setTopMargin(12)
        activationResponseTextView.setBottomMargin(12)
        activationResponseTextView.setLeftMargin(12)
        activationResponseTextView.setRightMargin(12)
        activationResponseTextView.getBuffer().setText(Str("Activation response will appear here..."), -1)

        responseScrolled.setChild(activationResponseTextView)
        responseFrame.setChild(responseScrolled)
        vbox.append(responseFrame)

        return vbox
    }

    private fun createOutputTab(): Widget {
        val vbox = Box(Orientation.VERTICAL, 20)
        vbox.setMarginTop(20)
        vbox.setMarginBottom(20)
        vbox.setMarginStart(20)
        vbox.setMarginEnd(20)

        // Console output section
        val consoleLabel = Label(Str("Console Output"))
        consoleLabel.setHalign(Align.START)
        consoleLabel.addCssClass(Str("heading"))
        vbox.append(consoleLabel)

        val helpLabel = Label(Str("Real-time status and progress information:"))
        helpLabel.setHalign(Align.START)
        helpLabel.addCssClass(Str("dim-label"))
        helpLabel.setMarginBottom(8)
        vbox.append(helpLabel)

        val consoleFrame = Frame(null as Str?)
        val scrolledWindow = ScrolledWindow()
        scrolledWindow.setPolicy(PolicyType.AUTOMATIC, PolicyType.AUTOMATIC)
        scrolledWindow.setVexpand(true)
        scrolledWindow.setHexpand(true)

        outputTextView = TextView()
        outputTextView.setEditable(false)
        outputTextView.setMonospace(true)
        outputTextView.setTopMargin(12)
        outputTextView.setBottomMargin(12)
        outputTextView.setLeftMargin(12)
        outputTextView.setRightMargin(12)

        scrolledWindow.setChild(outputTextView)
        consoleFrame.setChild(scrolledWindow)
        vbox.append(consoleFrame)

        return vbox
    }

    private fun updateActivationResponse() {
        val requestBuffer = activationRequestTextView.getBuffer()
        val startIter = TextIter()
        val endIter = TextIter()

        requestBuffer.getStartIter(startIter)
        requestBuffer.getEndIter(endIter)

        val requestText = requestBuffer.getText(startIter, endIter, false)
        val requestString = requestText.toString().trim()

        // Clean up
        requestText.destroy()
        startIter.destroy()
        endIter.destroy()

        if (requestString.isNotEmpty() && requestString != "Paste activation request from Burp Suite here...") {
            try {
                // Generate activation response using the existing logic
                val response = generateActivation(requestString)
                val responseStr = Str(response)
                activationResponseTextView.getBuffer().setText(responseStr, -1)
                responseStr.destroy()
            } catch (e: Exception) {
                val errorStr = Str("Error processing activation request: ${e.message}")
                activationResponseTextView.getBuffer().setText(errorStr, -1)
                errorStr.destroy()
            }
        } else {
            val placeholderStr = Str("Activation response will appear here...")
            activationResponseTextView.getBuffer().setText(placeholderStr, -1)
            placeholderStr.destroy()
        }
    }

    private fun appendOutput(text: String) {
        val buffer = outputTextView.getBuffer()
        val startIter = TextIter()
        val endIter = TextIter()

        buffer.getStartIter(startIter)
        buffer.getEndIter(endIter)

        val currentText = buffer.getText(startIter, endIter, false)
        val newText = if (currentText.toString().isEmpty()) text else "${currentText}\n$text"
        val textStr = Str(newText)
        buffer.setText(textStr.toString(), -1)

        // Clean up
        currentText.destroy()
        textStr.destroy()
        startIter.destroy()
        endIter.destroy()

        // Scroll to bottom
        val mark = buffer.getInsert()
        outputTextView.scrollToMark(mark, 0.0, false, 0.0, 0.0)
    }

    private fun showCalendarPicker() {
        // Create a popover with proper calendar widget
        val popover = Popover()
        popover.setParent(expirationEntry)

        val vbox = Box(Orientation.VERTICAL, 12)
        vbox.setMarginTop(16)
        vbox.setMarginBottom(16)
        vbox.setMarginStart(16)
        vbox.setMarginEnd(16)

        // Add the real GTK Calendar widget
        val calendarFrame = Frame(null as Str?)
        calendarFrame.setChild(expirationCalendar)
        vbox.append(calendarFrame)

        // Quick date options for convenience
        val quickDateLabel = Label(Str("Quick Options:"))
        quickDateLabel.setHalign(Align.START)
        quickDateLabel.addCssClass(Str("heading"))
        quickDateLabel.setMarginTop(8)
        vbox.append(quickDateLabel)

        val buttonBox = Box(Orientation.HORIZONTAL, 8)
        buttonBox.setHalign(Align.CENTER)

        val todayButton = Button()
        todayButton.setLabel(Str("Today"))
        todayButton.onClicked {
            val today = LocalDate.now()
            expirationCalendar.setYear(today.year)
            expirationCalendar.setMonth(today.monthValue - 1) // GTK months are 0-based
            expirationCalendar.setDay(today.dayOfMonth)
        }

        val oneYearButton = Button()
        oneYearButton.setLabel(Str("+1 Year"))
        oneYearButton.onClicked {
            val oneYear = LocalDate.now().plusYears(1)
            expirationCalendar.setYear(oneYear.year)
            expirationCalendar.setMonth(oneYear.monthValue - 1)
            expirationCalendar.setDay(oneYear.dayOfMonth)
        }

        val neverButton = Button()
        neverButton.setLabel(Str("Never"))
        neverButton.onClicked {
            val neverStr = Str("Never expires")
            expirationEntry.getBuffer().setText(neverStr, -1)
            neverStr.destroy()
            popover.popdown()
        }

        buttonBox.append(todayButton)
        buttonBox.append(oneYearButton)
        buttonBox.append(neverButton)
        vbox.append(buttonBox)

        // Action buttons
        val separator = Separator(Orientation.HORIZONTAL)
        separator.setMarginTop(8)
        separator.setMarginBottom(8)
        vbox.append(separator)

        val actionBox = Box(Orientation.HORIZONTAL, 8)
        actionBox.setHalign(Align.END)

        val selectButton = Button()
        selectButton.setLabel(Str("Select Date"))
        selectButton.addCssClass(Str("suggested-action"))
        selectButton.onClicked {
            // Get selected date from calendar and set in entry
            val year = expirationCalendar.getYear()
            val month = expirationCalendar.getMonth() + 1 // Convert from 0-based to 1-based
            val day = expirationCalendar.getDay()

            val dateStr = String.format("%04d-%02d-%02d", year, month, day)
            val displayStr = Str(dateStr)
            expirationEntry.getBuffer().setText(displayStr, -1)
            displayStr.destroy()
            popover.popdown()
        }

        val cancelButton = Button()
        cancelButton.setLabel(Str("Cancel"))
        cancelButton.onClicked { popover.popdown() }

        actionBox.append(cancelButton)
        actionBox.append(selectButton)
        vbox.append(actionBox)

        popover.setChild(vbox)
        popover.popup()
    }

    private fun updateExpirationDisplay() {
        try {
            val year = expirationCalendar.getYear()
            val month = expirationCalendar.getMonth() + 1 // Convert from 0-based to 1-based
            val day = expirationCalendar.getDay()

            val dateStr = String.format("%04d-%02d-%02d", year, month, day)
            val displayStr = Str("Selected: $dateStr")
            expirationEntry.getBuffer().setText(displayStr, -1)
            displayStr.destroy()
        } catch (e: Exception) {
            val errorStr = Str("Error reading calendar date")
            expirationEntry.getBuffer().setText(errorStr, -1)
            errorStr.destroy()
        }
    }

    private fun updateLaunchCommand() {
        try {
            val burpJar = File(System.getProperty("user.home"), ".local/share/BurpSuite/burpsuite_pro_v2025.9.3.jar")
            val loaderJar = File("build/libs/burploader-fat.jar")

            // Get the actual Java executable path
            val javaHome = System.getProperty("java.home")
            val javaExecutable = File(javaHome, "bin/java").absolutePath

            val command = buildString {
                appendLine("$javaExecutable \\")
                appendLine("  --add-opens=java.desktop/javax.swing=ALL-UNNAMED \\")
                appendLine("  --add-opens=java.base/java.lang=ALL-UNNAMED \\")
                appendLine("  --add-opens=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED \\")
                appendLine("  --add-opens=java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED \\")
                appendLine("  --add-opens=java.base/jdk.internal.org.objectweb.asm.Opcodes=ALL-UNNAMED \\")
                appendLine("  --add-opens=java.base/sun.security.util=ALL-UNNAMED \\")
                appendLine("  -javaagent:${loaderJar.absolutePath} \\")
                append("  -jar ${burpJar.absolutePath}")
            }

            val commandStr = Str(command)
            launchCommandTextView.getBuffer().setText(commandStr, -1)
            commandStr.destroy()
        } catch (e: Exception) {
            val errorStr = Str("Error generating launch command: ${e.message}")
            launchCommandTextView.getBuffer().setText(errorStr, -1)
            errorStr.destroy()
        }
    }

    private fun checkDownloadStatus() {
        // Move network operations to background thread to prevent UI freezing
        thread {
            try {
                val downloader = BurpSuiteDownloader()
                val installedVersion = downloader.getInstalledVersion()
                val currentVersion = downloader.getCurrentVersion()

                // Schedule UI updates on main thread
                Glib.idleAdd({ _, _ ->
                    updateDownloadStatusUI(installedVersion, currentVersion)
                    false // Don't repeat
                }, null)
            } catch (e: Exception) {
                Glib.idleAdd({ _, _ ->
                    updateDownloadButton("Download Burp Suite", "")
                    appendOutput("Error checking download status: ${e.message}")
                    false // Don't repeat
                }, null)
            }
        }
    }

    private fun updateDownloadStatusUI(installedVersion: String?, currentVersion: String?) {

        if (installedVersion == null) {
            // No Burp Suite installation found
            updateDownloadButton("Download Burp Suite", "")
            appendOutput("Burp Suite not found - ready to download")
        } else {
            // Check if installed JAR is valid
            val targetDir = File(System.getProperty("user.home"), ".local/share/BurpSuite")
            val burpJar = File(targetDir, "burpsuite_pro_v$installedVersion.jar")
            val fileSize = burpJar.length()
            val fileSizeStr = formatBytes(fileSize)

            if (fileSize <= 1000000) { // <= 1MB indicates incomplete file
                updateDownloadButton("Re-download Burp Suite", "")
                appendOutput("Burp Suite file incomplete - ready to re-download")
                return
            }

            // Validate file integrity with checksum if we have the current version info
            if (currentVersion != null && installedVersion == currentVersion) {
                // File is the latest version, validate its checksum
                appendOutput("Validating file integrity...")

                // Get expected checksum for this version in background
                thread {
                    try {
                        val downloader = BurpSuiteDownloader()
                        val versionInfo = downloader.fetchLatestVersion()

                        if (versionInfo != null) {
                            val (_, _, expectedSha256) = versionInfo
                            val actualSha256 = downloader.calculateSHA256(burpJar)

                            // Update UI on main thread
                            Glib.idleAdd({ _, _ ->
                                if (actualSha256.equals(expectedSha256, ignoreCase = true)) {
                                    // File is valid
                                    updateDownloadButton("✅ Burp Suite v$installedVersion ($fileSizeStr)", "suggested-action")
                                    appendOutput("✅ Burp Suite v$installedVersion is installed and verified ($fileSizeStr)")
                                } else {
                                    // File is corrupted
                                    updateDownloadButton("Re-download Burp Suite (corrupted)", "")
                                    appendOutput("Burp Suite file is corrupted - checksum mismatch")
                                    appendOutput("   Expected: $expectedSha256")
                                    appendOutput("   Actual:   $actualSha256")
                                }
                                false
                            }, null)
                        } else {
                            // Couldn't get version info, assume file is OK
                            Glib.idleAdd({ _, _ ->
                                updateDownloadButton("✅ Burp Suite v$installedVersion ($fileSizeStr) - couldn't verify", "suggested-action")
                                appendOutput("✅ Burp Suite v$installedVersion is installed ($fileSizeStr) - couldn't verify integrity")
                                false
                            }, null)
                        }
                    } catch (e: Exception) {
                        // Error during validation, assume file is OK but log the error
                        Glib.idleAdd({ _, _ ->
                            updateDownloadButton("✅ Burp Suite v$installedVersion ($fileSizeStr) - couldn't verify", "suggested-action")
                            appendOutput("Couldn't verify file integrity: ${e.message}")
                            false
                        }, null)
                    }
                }
            } else if (currentVersion != null && installedVersion != currentVersion) {
                // Update available
                updateDownloadButton("Update to v$currentVersion ($fileSizeStr)", "")
                appendOutput("Burp Suite v$installedVersion installed, v$currentVersion available")
            } else {
                // Couldn't check latest version, assume file is OK
                updateDownloadButton("✅ Burp Suite v$installedVersion ($fileSizeStr) - couldn't check for updates", "suggested-action")
                appendOutput("✅ Burp Suite v$installedVersion is installed ($fileSizeStr)")
            }
        }
    }

    private fun updateDownloadButton(text: String, cssClass: String) {
        // Remove existing CSS classes
        downloadButton.removeCssClass(Str("suggested-action"))
        downloadButton.removeCssClass(Str("destructive-action"))

        // Set new text and CSS class
        val labelStr = Str(text)
        downloadButton.setLabel(labelStr)
        labelStr.destroy()

        val classStr = Str(cssClass)
        downloadButton.addCssClass(classStr)
        classStr.destroy()
    }

    private fun handleDownloadAction() {
        // Check button text to determine action
        val buttonLabel = downloadButton.getLabel().toString()

        // If button indicates download/re-download/update is needed, start download
        if (buttonLabel.contains("Download Burp Suite") ||
            buttonLabel.contains("Re-download") ||
            buttonLabel.contains("Update to") ||
            buttonLabel.contains("corrupted")) {
            // Start download
            downloadBurpSuite()
        } else {
            // File exists and is verified - show info dialog
            showDownloadInfoDialog()
        }
    }

    private fun showDownloadInfoDialog() {
        // Create an info dialog showing current status
        val dialog = AlertDialog(Str("Burp Suite is already downloaded and up to date.\n\nVersion: v2025.9.3\nLocation: ~/.local/share/BurpSuite/\n\nClick 'Launch Burp Suite' to start the application."))
        dialog.show(window)
    }

    private fun createDownloadProgressDialog(): Window {
        val progressWindow = Window()
        progressWindow.setTitle(Str("Downloading Burp Suite"))
        progressWindow.setDefaultSize(400, 200)
        progressWindow.setTransientFor(window)
        progressWindow.setModal(true)

        val vbox = Box(Orientation.VERTICAL, 16)
        vbox.setMarginTop(20)
        vbox.setMarginBottom(20)
        vbox.setMarginStart(20)
        vbox.setMarginEnd(20)

        // Title
        val titleLabel = Label(Str("Downloading Burp Suite Pro"))
        titleLabel.addCssClass(Str("title-3"))
        vbox.append(titleLabel)

        // Progress bar
        val progressBar = ProgressBar()
        progressBar.setHexpand(true)
        vbox.append(progressBar)

        // Status label
        val statusLabel = Label(Str("Preparing download..."))
        vbox.append(statusLabel)

        // Speed and size label
        val detailsLabel = Label(Str(""))
        detailsLabel.addCssClass(Str("dim-label"))
        vbox.append(detailsLabel)

        // Store references for updates (using user data)
        progressWindow.setData(Str("progressBar"), progressBar)
        progressWindow.setData(Str("statusLabel"), statusLabel)
        progressWindow.setData(Str("detailsLabel"), detailsLabel)

        progressWindow.setChild(vbox)
        return progressWindow
    }

    private fun updateProgressDialog(dialog: Window, progress: DownloadProgress, downloaded: String, total: String) {
        try {
            val progressBar = dialog.getData(Str("progressBar")) as? ProgressBar
            val statusLabel = dialog.getData(Str("statusLabel")) as? Label
            val detailsLabel = dialog.getData(Str("detailsLabel")) as? Label

            progressBar?.setFraction(progress.percentage / 100.0)

            val statusText = Str("${progress.percentage}% complete")
            statusLabel?.setText(statusText)
            statusText.destroy()

            val detailsText = Str("$downloaded / $total @ ${progress.speed}")
            detailsLabel?.setText(detailsText)
            detailsText.destroy()
        } catch (e: Exception) {
            // Ignore UI update errors
        }
    }
}

// Entry point for gradle run
fun main(args: Array<String>) {
    val app = GtkBurpKeygenApp()
    app.run(args)
}