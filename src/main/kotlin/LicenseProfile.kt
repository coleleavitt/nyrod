import java.time.LocalDateTime
import java.time.ZoneOffset

data class LicenseProfile(
    val licenseName: String = "Licensed to John Doe",
    val licenseType: LicenseType = LicenseType.PROFESSIONAL,
    val expirationDate: LocalDateTime? = null, // null = no expiration (2099)
    val customId: String? = null, // null = auto-generate
    val profileName: String = "Default Profile"
)

enum class LicenseType(val value: String, val displayName: String) {
    PROFESSIONAL("license", "Burp Suite Professional"),
    ENTERPRISE("enterprise", "Burp Suite Enterprise"),
    TRIAL("trial", "Trial License"),
    EDUCATIONAL("educational", "Educational License")
}

object LicenseProfileManager {

    private val predefinedProfiles = mapOf(
        "Professional" to LicenseProfile(
            licenseName = "Licensed to John Doe",
            licenseType = LicenseType.PROFESSIONAL,
            profileName = "Professional"
        ),
        "Enterprise" to LicenseProfile(
            licenseName = "Licensed to John Doe",
            licenseType = LicenseType.ENTERPRISE,
            profileName = "Enterprise"
        ),
        "Trial" to LicenseProfile(
            licenseName = "Licensed to John Doe",
            licenseType = LicenseType.TRIAL,
            expirationDate = LocalDateTime.now().plusDays(30),
            profileName = "Trial"
        ),
        "Educational" to LicenseProfile(
            licenseName = "Licensed to John Doe",
            licenseType = LicenseType.EDUCATIONAL,
            profileName = "Educational"
        )
    )

    fun getPredefinedProfiles(): Map<String, LicenseProfile> = predefinedProfiles

    fun createDefaultProfile(): LicenseProfile = LicenseProfile()

    fun generateLicenseWithProfile(profile: LicenseProfile): String {
        info("Generating license with profile: ${profile.profileName}")
        info("License details: name=${profile.licenseName}, type=${profile.licenseType.value}")

        val licenseId = profile.customId ?: getRandomString()
        val expiration = when {
            profile.expirationDate != null -> {
                val timestamp = profile.expirationDate.toEpochSecond(ZoneOffset.UTC) * 1000
                timestamp.toString()
            }
            profile.licenseType == LicenseType.TRIAL -> {
                // Default trial: 30 days from now
                val thirtyDaysFromNow = LocalDateTime.now().plusDays(30).toEpochSecond(ZoneOffset.UTC) * 1000
                thirtyDaysFromNow.toString()
            }
            else -> "4102415999000" // Year 2099 (effectively never expires)
        }

        val al = mutableListOf(
            licenseId,
            profile.licenseType.value,
            profile.licenseName,
            expiration,
            "1",
            "full"  // Always use "full" for all license types
        )

        // Add cryptographic signatures
        al.add(getSign(privateKey2048, getSignatureBytes(al), "SHA256withRSA"))
        al.add(getSign(privateKey1024, getSignatureBytes(al), "SHA1withRSA"))

        info("License generated successfully with expiration: $expiration")
        return prepareArray(al)
    }

    fun validateProfile(profile: LicenseProfile): List<String> {
        val errors = mutableListOf<String>()

        // Validate license name
        if (profile.licenseName.isBlank()) {
            errors.add("License name cannot be empty")
        }
        if (profile.licenseName.length > 100) {
            errors.add("License name too long (max 100 characters)")
        }


        // Validate expiration date
        if (profile.expirationDate != null && profile.expirationDate.isBefore(LocalDateTime.now())) {
            errors.add("Expiration date cannot be in the past")
        }

        // Validate custom ID
        if (!profile.customId.isNullOrBlank()) {
            if (profile.customId.length != 32) {
                errors.add("Custom ID must be 32 characters long (or leave blank for auto-generation)")
            }
            if (!profile.customId.matches(Regex("[A-Z0-9]+"))) {
                errors.add("Custom ID must contain only uppercase letters and numbers")
            }
        }

        return errors
    }

    fun getProfileDescription(profile: LicenseProfile): String {
        val expiration = when {
            profile.expirationDate != null -> "Expires ${profile.expirationDate.toLocalDate()}"
            profile.licenseType == LicenseType.TRIAL -> "30-day trial"
            else -> "Never expires"
        }

        return "${profile.licenseType.displayName} • $expiration"
    }
}

// Helper functions for date handling
fun LocalDateTime.toDisplayString(): String {
    return this.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}

fun String.toLocalDateTime(): LocalDateTime? {
    return try {
        LocalDateTime.parse(this, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    } catch (e: Exception) {
        null
    }
}