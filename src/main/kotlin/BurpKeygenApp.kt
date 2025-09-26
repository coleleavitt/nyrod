import javafx.application.Application
import javafx.application.Platform
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.stage.Stage
import java.io.IOException

class BurpKeygenApp : Application() {

    override fun start(primaryStage: Stage) {
        try {
            // Set up SSL trust before any network calls
            trustAllHosts()

            // Load FXML
            val fxmlLoader = FXMLLoader(javaClass.getResource("/burp-keygen.fxml"))
            val root: Parent = fxmlLoader.load()
            val controller: BurpKeygenController = fxmlLoader.getController()

            // Create scene
            val scene = Scene(root, 1000.0, 700.0)

            // Initialize theme manager
            ThemeManager.initialize(scene)
            ThemeManager.startSystemThemeMonitoring()

            // Configure stage
            primaryStage.title = "Burp Suite Pro Loader & Keygen"
            primaryStage.scene = scene
            primaryStage.minWidth = 800.0
            primaryStage.minHeight = 600.0

            // Handle auto-run logic
            primaryStage.setOnShown {
                handleAutoRun(controller)
            }

            primaryStage.show()

        } catch (e: IOException) {
            e.printStackTrace()
            Platform.exit()
        }
    }

    private fun handleAutoRun(controller: BurpKeygenController) {
        if (controller.shouldAutoRun()) {
            try {
                val cmd = controller.getCurrentCommand()
                Runtime.getRuntime().exec(cmd)

                // Check if we should close after auto-run
                if (controller.shouldIgnoreUpdate()) {
                    Platform.exit()
                }
            } catch (e: IOException) {
                e.printStackTrace()
                // If auto-run fails, continue showing the GUI
            }
        }
    }

    override fun stop() {
        Platform.exit()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            // Set JavaFX properties for better Wayland support
            System.setProperty("prism.order", "sw")
            System.setProperty("prism.verbose", "true")
            System.setProperty("javafx.platform", "desktop")

            // Enable better font rendering
            System.setProperty("prism.lcdtext", "false")
            System.setProperty("prism.subpixeltext", "false")

            // Launch JavaFX application
            launch(BurpKeygenApp::class.java, *args)
        }
    }
}

// Entry point for gradle run
fun main(args: Array<String>) {
    BurpKeygenApp.main(args)
}