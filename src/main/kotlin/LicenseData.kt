import java.time.LocalDate
import java.time.ZoneOffset

data class LicenseData(
    var licenseName: String = "Licensed to John Doe",
    var licenseType: String = "license",  // "license", "trial", "enterprise"
    var expirationDate: LocalDate = LocalDate.of(2099, 12, 31),
    var licenseLevel: String = "full",  // "full", "professional", "community", "enterprise"
    var companyName: String = "",
    var email: String = "",
    var licenseId: String = "",
    var features: MutableSet<String> = mutableSetOf("scanner", "intruder", "repeater", "sequencer", "decoder", "comparer", "extender"),
    var notes: String = ""
) {

    // Convert to the array format needed for license generation
    fun toArray(): List<String> {
        val randomId = licenseId.ifBlank { getRandomString() }
        val expirationTimestamp = (expirationDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000).toString()

        return listOf(
            randomId,
            licenseType,
            licenseName,
            expirationTimestamp,
            "1",
            licenseLevel
        )
    }

    // Create a display-friendly summary
    fun getSummary(): String {
        val expiryText = if (expirationDate.year >= 2099) "Never" else expirationDate.toString()
        val featuresText = if (features.isEmpty()) "All" else features.joinToString(", ")

        return buildString {
            appendLine("License Summary:")
            appendLine("• Name: $licenseName")
            if (companyName.isNotBlank()) appendLine("• Company: $companyName")
            if (email.isNotBlank()) appendLine("• Email: $email")
            appendLine("• Type: ${licenseType.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}")
            appendLine("• Level: ${licenseLevel.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}")
            appendLine("• Expires: $expiryText")
            appendLine("• Features: $featuresText")
            if (notes.isNotBlank()) appendLine("• Notes: $notes")
        }
    }

}

