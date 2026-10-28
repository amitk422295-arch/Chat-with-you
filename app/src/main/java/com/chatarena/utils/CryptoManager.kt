package com.chatarena.utils

import android.content.Context
import android.util.Base64
import android.util.Log
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    private const val TAG = "CryptoManager"
    private const val E2E_PREFIX = "E2E:"

    // Cache for Shared Keys to avoid recalculating again and again
    private val sharedKeyCache = mutableMapOf<String, SecretKey>()
    // Cache for Public Keys
    private val publicKeyCache = mutableMapOf<String, String>()

    // 1. Generate ECDH Key Pair
    fun generateKeyPair(): Pair<String, String> {
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(ECGenParameterSpec("secp256r1")) // P-256
        val keyPair = keyGen.generateKeyPair()

        val pubBase64 = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
        val privBase64 = Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)

        return Pair(pubBase64, privBase64)
    }

    // 2. Ensure Keys Exist in SharedPreferences
    fun ensureE2EKeys(context: Context): String {
        val prefs = context.getSharedPreferences("ChatArenaPrefs", Context.MODE_PRIVATE)
        var pubKey = prefs.getString("chatAppPublicKey", null)
        var privKey = prefs.getString("chatAppPrivateKey", null)

        if (pubKey == null || privKey == null) {
            val keys = generateKeyPair()
            pubKey = keys.first
            privKey = keys.second
            prefs.edit()
                .putString("chatAppPublicKey", pubKey)
                .putString("chatAppPrivateKey", privKey)
                .apply()
        }
        return pubKey
    }

    // 3. Import Keys
    private fun importPrivateKey(base64Key: String): PrivateKey {
        val bytes = Base64.decode(base64Key, Base64.NO_WRAP)
        val spec = PKCS8EncodedKeySpec(bytes)
        val kf = KeyFactory.getInstance("EC")
        return kf.generatePrivate(spec)
    }

    private fun importPublicKey(base64Key: String): PublicKey {
        val bytes = Base64.decode(base64Key, Base64.NO_WRAP)
        val spec = X509EncodedKeySpec(bytes)
        val kf = KeyFactory.getInstance("EC")
        return kf.generatePublic(spec)
    }

    // 4. Derive Shared Secret (AES Key)
    private fun deriveSharedKey(myPrivKeyBase64: String, targetPubKeyBase64: String, cacheKey: String): SecretKey {
        if (sharedKeyCache.containsKey(cacheKey)) {
            return sharedKeyCache[cacheKey]!!
        }

        val myPrivate = importPrivateKey(myPrivKeyBase64)
        val targetPublic = importPublicKey(targetPubKeyBase64)

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(myPrivate)
        keyAgreement.doPhase(targetPublic, true)

        val sharedSecret = keyAgreement.generateSecret()
        
        // JS code directly derives 256-bit AES key. 
        // In Java, we use the shared secret to create an AES key. 
        // To match Web Crypto's deriveKey exactly, we should use SHA-256 of the shared secret as the AES key.
        val sha256 = java.security.MessageDigest.getInstance("SHA-256")
        val aesKeyBytes = sha256.digest(sharedSecret)
        
        val secretKey = SecretKeySpec(aesKeyBytes, "AES")
        sharedKeyCache[cacheKey] = secretKey
        return secretKey
    }

    // 5. Encrypt Text
    fun encryptText(context: Context, text: String, targetCode: String, targetPubKeyBase64: String): String {
        try {
            val prefs = context.getSharedPreferences("ChatArenaPrefs", Context.MODE_PRIVATE)
            val myPrivKey = prefs.getString("chatAppPrivateKey", null) ?: throw Exception("No private key")
            
            val cacheKey = "${targetCode}::${myPrivKey.takeLast(24)}::${targetPubKeyBase64.takeLast(16)}"
            val sharedKey = deriveSharedKey(myPrivKey, targetPubKeyBase64, cacheKey)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(12)
            SecureRandom().nextBytes(iv)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, sharedKey, spec)

            val ciphertext = cipher.doFinal(text.toByteArray(Charsets.UTF_8))

            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val cipherBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)

            return "$E2E_PREFIX$ivBase64:$cipherBase64"
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed: ${e.message}")
            throw e
        }
    }

    // 6. Decrypt Text
    fun decryptText(context: Context, cipherText: String, targetCode: String, targetPubKeyBase64: String): String {
        if (!cipherText.startsWith(E2E_PREFIX)) {
            return cipherText // Plain text
        }

        try {
            val parts = cipherText.split(":")
            if (parts.size != 3) return "🔒 Message Unavailable"

            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)

            val prefs = context.getSharedPreferences("ChatArenaPrefs", Context.MODE_PRIVATE)
            val myPrivKey = prefs.getString("chatAppPrivateKey", null) ?: return "🔒 Message Unavailable"

            val cacheKey = "${targetCode}::${myPrivKey.takeLast(24)}::${targetPubKeyBase64.takeLast(16)}"
            val sharedKey = deriveSharedKey(myPrivKey, targetPubKeyBase64, cacheKey)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, sharedKey, spec)

            val decryptedBytes = cipher.doFinal(ciphertext)
            return String(decryptedBytes, Charsets.UTF_8)

        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed: ${e.message}")
            return "🔒 Message Unavailable"
        }
    }
}
