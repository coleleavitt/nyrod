import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.RSAPrivateKey
import java.security.spec.RSAPrivateKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

internal fun decrypt(data: ByteArray): ByteArray {
    return try {
        val spec = SecretKeySpec(encryptionKey, "DES")
        val cipher = Cipher.getInstance("DES")
        cipher.init(Cipher.DECRYPT_MODE, spec)
        cipher.doFinal(data)
    } catch (e: Exception) {
        e.printStackTrace()
        throw RuntimeException(e)
    }
}

fun burpFilter(obj: Array<Any>) {
    val data = obj[0] as ByteArray
    val decode = Base64.getDecoder().decode(data)
    val decrypt = decrypt(decode)
    val str = String(decrypt)
    val strs = str.split("\u0000").toTypedArray()
    obj[0] = strs.copyOf(strs.size - 2)
}

fun bountyFilter(url: String, data: ByteArray): ByteArray? {
    return when {
        url == "https://api.licensespring.com/api/v4/activate_license" -> {
            val doSign = String(data)
            val jsonPayload = getText(doSign, "hardware_id") + "#" + getText(doSign, "license_key") + "#2099-12-31t14:58:33.213z"
            val signature = getsign(jsonPayload.lowercase())
            val responseJson = """
            {"license_signature":"$signature","license_type":"perpetual","is_trial":false,"validity_period":"2099-12-31T20:28:33.213+05:30","max_activations":99,"times_activated":99,"transfer_count":99,"prevent_vm":false,"customer":{"email":"hello@example.com","first_name":"Zer0Day","last_name":"Lab","company_name":"Zer0DayLab","phone":"+86","reference":"love"},"product_details":{"product_name":"Burp Bounty Pro","short_code":"burpbountyprostripe","allow_trial":false,"trial_days":0,"authorization_method":"license-key"},"allow_overages":false,"max_overages":0,"is_floating_cloud":false,"floating_users":0,"floating_timeout":0}
            """.trimIndent()
            responseJson.toByteArray()
        }
        url == "https://api.licensespring.com/api/v4/product_details?product=burpbountyprostripe" -> {
            "{\"product_name\":\"Burp Bounty Pro\",\"short_code\":\"burpbountyprostripe\",\"allow_trial\":false,\"trial_days\":0,\"authorization_method\":\"license-key\"}".toByteArray()
        }
        url.startsWith("https://api.licensespring.com/api/v4/check_license?app_name=Burp") -> {
            val doSign = getparam(url, "hardware_id") + "#" + getparam(url, "license_key") + "#2099-12-31t14:58:33.213z"
            val signature = getsign(doSign.lowercase())
            val responseJson = """
            {"license_signature":"$signature","license_type":"perpetual","is_trial":false,"validity_period":"2099-12-31T20:28:33.213+05:30","max_activations":0,"times_activated":0,"transfer_count":0,"prevent_vm":false,"customer":{"email":"hello@example.com","first_name":"Zer0Day","last_name":"Lab","company_name":"Zer0DayLab","phone":"+86","reference":"love"},"product_details":{"product_name":"Burp Bounty Pro","short_code":"burpbountyprostripe","allow_trial":false,"trial_days":0,"authorization_method":"license-key"},"allow_overages":false,"max_overages":0,"is_floating_cloud":false,"floating_users":0,"floating_timeout":0,"license_active":true,"license_enabled":true,"is_expired":false}
            """.trimIndent()
            responseJson.toByteArray()
        }
        else -> null
    }
}

fun getText(json: String, label: String): String? {
    val before = "\"$label\":\""
    val start = json.indexOf(before)
    if (start != -1) {
        val startIndex = start + before.length
        val endIndex = json.indexOf("\"", startIndex)
        if (endIndex != -1) {
            return json.substring(startIndex, endIndex)
        }
    }
    return null
}

fun getparam(url: String, label: String): String? {
    val before = "$label="
    return url.substringAfter('?').split('&').find { it.startsWith(before) }?.substringAfter(before)
}

fun getsign(dover: String): String {
    val encodedN = "NzcyNDY2NDg1OTkzNTMyNjE2MjY1NjAyMDA0Nzg3NTkzOTkwNTcxOTk4MzU0NzYyNzY5NTE3NTU2ODI0OTQ3NDYyMzY0NzMzODQ2OTg4ODk2NzY0NzkxNzg2NDE4Mjg0MDQ4MzA4NDA4MDM1ODk4MTIzODAwMDg4Mzg1MTQ1NTk5OTE0NDgyODY2OTgyMTIxMzM3MjQyMjY3MTExOTk2NzMwNTg5ODMzMDA4OTY1NTg2OTA0NjE5NjI0MjEwNjAwMTQzODE1MDM0NTU1MTA3NjI2MjgxMzA2NzkwNjkwMjQ3MzgwMjc3NDAxMjIzNjU4MjEzNjg2ODM2MjYwMDUxNzQwNTEzMzY5NTI1MjI5OTQ1MTQ2NTQ1NzQ2MjMyMjg4NjQ0ODM0MDExMDgyMjcyMzczOTIzODQwNjc0MTgzODQxMTUyNzIxMjgyOTM0ODk2MTQ3NjM2NjU5Njg1MTk2MTEwMTkzMTcwODU5MDM2MjMwNjEzOTQyNDczMDU3MzUwNTU4Njk2MDc5NzgxMzI5NzI0NzIzNTM4NjEyMDY1NDUxMjcxMTE3OTQ2NjUzMTcxODkxNDYzNzAxMjA2MDA1MTMyNDc0MjQ5MTQzMzc3MjgwOTU2NjM3Mjc0ODEyMDE3NzIyOTY2OTYyMDM4MTc3NjU4ODI2NjgyOTI4MTc3NDE5OTY3MTIwNjE1NzIzNDEyNzE1MjgwNjI3MTQwMzUyNjY2NTEzNzYxODg3NjU1NjgzODgxMTI3OTQxMDYyNTIxNjA4NjIyMTUwMDU3Mzc1MzAyNzczODE3MTI4MDQzMzQxNTUxMTA0MzM1OTI1MzA3NTI0MzYyNDYzMTU4NTMyMDE0MzQ1OTIwNDAxNTI5MTMxNjg5OTcyNTExNzExMTQ1MzY2Mzg0MzEzMzU3MzMxMjE5OTE4MDA3OTAyMTYyMjcyOTQ3ODEwMDkyNDM0NDAxNDIxMDc0NDM2Mjk3MTMwNTQzNDIwODU3Njc5MTczMjUwODc5MjIxNzAzMjM5NDYxMTY3MjQ1MTI4NzE4MDI3MjEwNTg1NDcwMDk3NzQxMzM4NDc2MTY3NjY0MjMxMzg2MjI0MzkzODI5MTczNzUzNTk3MzI5Njg0OTgxNTQ5NDQyMDMxMDA0ODg2MzA5MTc2ODA0Mjc0NTM2NTI0NDIxMjY5ODk2Njk5NjEzMTQ4NDI3MDE3MDExNjQ4ODA1OTY4MzI4MjEzODc5NTYxNzA0NjY0ODIzMjQ5ODA1MzQxNDMwODUwNzA4MzQ0OTU1OTIyODg2ODIyMTkxMzgwMjA2NTQzMzkzNTI1MzkxNTE5NzIyMjIyNTg2MzM5NjEyNzcwNTMxMDc0NTc3MzAzMzQ5NjI1NzkwMjA4MDQ3MDQ3MDM1OTMzOTk2MTM3ODMxMDg2NDYxNDk2NzkzMjM1NDEwNDQ1NzY5NjU5OTgyMzU1NTY3MTgwMDczNTA5NDA1MDc3MDk0Nzg0OTc1NzEzNTc1OTM3NjcxNTYwMzk5NzM0NTQ0ODczOTY3MzM5MjgxOTAwNDQzNDU4OTA0MDUyMzQ1Njk4NzUyODE3NTA0NDQ5ODEzNTA4NjY0ODM1MjQ3MjA2NzI3NDkzODI4NzQwNTAwNzUxNzI3MzA1OTQ4ODcwMTk3"
    val encodedD = "MTc0OTczOTA3NjMzNDU4ODM1MjkwOTQ4MDQwOTcwMzE5NTU2NzM5NTcxNzc3NDE1MDk4NTQ3MjA0MzQzNTk3NDM0NzEzMjgzNzAxNzAzNjIwNDM1Mzc3NDgyNDgxMzM3NjY3MjI4MjU3NTg0MTU3MTU3NzU1MzQ5MDcxMTczMzI4NTEyNjAzNTM5MzgwMDM4NjIzMjQxMjQ0NzIwOTYwNTQ4NDc4MDUxNjUwNDg4OTQ3Mjc1NTcwNzIwNjgzMDcwMDA4MzE0OTA4OTUxNzQ1OTI1Mzg3ODg3NzQ3NDk4MzA5NjkzMTUzNzYwNzk0MTM0NzA1MzE0MjY2NTEwMjIyNzQ1NzQ1NDcxNTc0ODY2Njk0MTY4MTg3MDAyODA0ODAyMjQ4MDgyMTY1NzE1Nzk5MjE4NjIzMDU4OTU2MTM1NjA0NjQ5NDY3NDM3NDA0MzQ0NjE5MjQ4MTUzMTUwNTM2ODkxNDYzMTA2NjYzNDQ4MDE5MzM5NjcwMzU0MzQyMzc3MTE1ODg2MzQzNjU4NjAyNjE3ODExNjMyMDY1Nzk2NTk3NzA5OTkyMDAzMjY2MzQzMjM3MDg0MDY5MjE4OTYyNTEyNjUwNTY3MjkwNzQzMTc5MjA4NzEwODA5ODQxNTMwNzg1NjI0MDY4MTA1OTA1NzgzNjgzNzUyODEyNDM4NjgwNDMxMDgzMjg5NDQ1ODcxMjE0MDk4MDA0MzMxODgxNzY2NTUwMTQ0MTIwNDU0ODM2NjE0NjY1Njc0MTI0MTMwMzg4NjM1MzYzNDk4NTA4MjY4MTQ4OTQzNTc3OTE0ODE3NjY1MjI5NDU4NzY3MDEyODI2Mzg0MTcwMjkzNDQyNDgxMzUzODg1NTEzOTQyMjg2Mzg2ODcxNDIyMjI5OTIxNzQwNjc1NTgyNjQ5NzIxMzk0MDA0NzM3NzEzMTQ2NTc5MDkyMzM2NDI4NzM0MTIyMjIzMDczMTY2MDgxODY4OTg3Mjg1MzAyODI2NjA5NTc5MzQwNjMwNzY0ODgyODkwOTk5NDM2Nzk5ODE0NDg4Njk1NzYxODYzMjQwMzc0MzM3Nzc1NDUxMTQ0MjAyMzM2MTAxMDQ1MDA1MDYyNzY5OTUxNjA2MzMwOTMxMzE2NzI4NjIwODU5NDAzMzQwOTQ5MTk2MDQ4NTQ0MDE2ODMxMzMxMjE1ODgyMDE5NjA2OTEwMjM2MTgxMTIwMDQ1NjE5NDQ3NDgyMjA4NzYyNDM4NTg2Mzg4MTUyMzk3MTgwODIwMTMxNTIyMjA1ODczMDc4MDYzOTEwMjExMTg1ODAzNDY4NTkwNTI1OTcyOTA3MTYzNjEzMTk5NDEzMDA0OTAzOTg2NTU3MDc3NjIzNzU4NDcxMjQ4MzgwMjcxNzgwNzE1MzQ0Nzk2MzcwNDg4ODg1MjIwMDc3MDc1MTUwNzI0ODQzMDA5MjM2NDYwMTU2NTcxNjUwNjIyMjQ5MTI4ODkzODY1OTc2MjM2MTI2NzAzNjc5NDc0MTUzODQ0NDMzMjA0MTQzNjc2MjYwMzYzODY0NDcxODMzMTQxMTQ0NDY2NzkwNjUzNTM0MTYwNTA2ODA1MDAyMzgzNDQyMjUzMTYwNzQ2MzUwMzQ0OTcyOTM4NDQyODU0OTQ0Mzk5MTk1OTMz"
    val n = String(Base64.getDecoder().decode(encodedN))
    val d = String(Base64.getDecoder().decode(encodedD))

    val sign = Signature.getInstance("SHA256withRSA")
    sign.initSign(getPriKeyByND(n, d))
    sign.update(dover.toByteArray(StandardCharsets.UTF_8))
    val signature = sign.sign()
    return Base64.getEncoder().encodeToString(signature)
}

fun getPriKeyByND(n: String, d: String): RSAPrivateKey {
    val spec = RSAPrivateKeySpec(BigInteger(n), BigInteger(d))
    val kf = KeyFactory.getInstance("RSA")
    return kf.generatePrivate(spec) as RSAPrivateKey
}