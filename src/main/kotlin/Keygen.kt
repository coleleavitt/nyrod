import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*


fun generateActivation(activationRequest: String): String {
    val request = decodeActivationRequest(activationRequest)
        ?: return "Error decoding activation request :-("

    val al = mutableListOf(
        "0.4315672535134567",
        request[0],
        "activation",
        request[1],
        "True",
        "",
        request[2],
        request[3]
    )
    al.add(getSign(privateKey2048, getSignatureBytes(al), "SHA256withRSA"))
    al.add(getSign(privateKey1024, getSignatureBytes(al), "SHA1withRSA"))
    return prepareArray(al)
}


fun getPriKeyByHex(hexStr: String): RSAPrivateKey? {
    val priData = BigInteger(hexStr, 16).toByteArray()
    return getPriKeyByBytes(priData)
}

fun getPriKeyByBytes(priData: ByteArray): RSAPrivateKey? {
    return try {
        val keyFactory = KeyFactory.getInstance("RSA")
        val pkcs8EncodedKeySpec = PKCS8EncodedKeySpec(priData)
        keyFactory.generatePrivate(pkcs8EncodedKeySpec) as RSAPrivateKey
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun generateLicense(licenseName: String): String {
    val al = mutableListOf(
        getRandomString(),
        "license",
        licenseName,
        "4102415999000",
        "1",
        "full"
    )
    al.add(getSign(privateKey2048, getSignatureBytes(al), "SHA256withRSA"))
    al.add(getSign(privateKey1024, getSignatureBytes(al), "SHA1withRSA"))
    return prepareArray(al)
}


internal fun decodeActivationRequest(activationRequest: String): List<String>? {
    return try {
        val ar = getParamsList(activationRequest)
        if (ar.size != 5) {
            warn("Activation Request Decoded to wrong size! The following was Decoded: \n$ar")
            null
        } else {
            ar
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}


internal fun getParamsList(data: String): List<String> {
    val rawBytes = decrypt(Base64.getDecoder().decode(data))
    val ar = mutableListOf<String>()
    var from = 0
    for (i in rawBytes.indices) {
        if (rawBytes[i] == 0.toByte()) {
            ar.add(String(rawBytes, from, i - from))
            from = i + 1
        }
    }
    ar.add(String(rawBytes, from, rawBytes.size - from))
    return ar
}