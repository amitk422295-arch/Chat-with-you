package com.chatarena.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Base64
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    private const val TAG = "CryptoManager"
    private const val E2E_PREFIX = "E2E:"
    private const val MEDIA_CHUNK_SIZE = 5 * 1024 * 1024 // 5MB
    private const val CLOUDINARY_CLOUD = "gr8tp1tg"

    // --- Caching (With Eviction just like JS) ---
    private val publicKeyCache = object : LruCache<String, String>(200) {}
    private val sharedKeyCache = object : LruCache<String, SecretKey>(200) {}

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    // --- Base64 Helpers ---
    private fun uint8ToBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun base64ToUint8(b64: String): ByteArray = Base64.decode(b64, Base64.NO_WRAP)

    // ==========================================
    // 🔒 KEY MANAGEMENT
    // ==========================================

    fun generateKeyPair(): Pair<String, String> {
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
        val keyPair = keyGen.generateKeyPair()
        val pubBase64 = uint8ToBase64(keyPair.public.encoded)
        val privBase64 = uint8ToBase64(keyPair.private.encoded)
        return Pair(pubBase64, privBase64)
    }

    fun ensureE2EKeys(context: Context): String {
        val prefs = context.getSharedPreferences("ChatArenaPrefs", Context.MODE_PRIVATE)
        val currentUserCode = TokenManager.getUserCode(context) ?: ""
        val owner = prefs.getString("chatAppKeysOwner", "")

        var privKey = prefs.getString("chatAppPrivateKey", null)
        var pubKey = prefs.getString("chatAppPublicKey", null)

        if (privKey != null && pubKey != null && owner != currentUserCode) {
            privKey = null; pubKey = null // Invalidate old keys
        }

        if (privKey == null || pubKey == null) {
            val keys = generateKeyPair()
            privKey = keys.second
            pubKey = keys.first
            prefs.edit()
                .putString("chatAppPrivateKey", privKey)
                .putString("chatAppPublicKey", pubKey)
                .putString("chatAppKeysOwner", currentUserCode)
                .apply()
        }
        return pubKey
    }

    fun resetE2EKeys(context: Context) {
        val prefs = context.getSharedPreferences("ChatArenaPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("chatAppPrivateKey")
            .remove("chatAppPublicKey")
            .remove("chatAppKeysOwner")
            .apply()
    }

    fun hasLocalKeys(context: Context): Boolean {
        val prefs = context.getSharedPreferences("ChatArenaPrefs", Context.MODE_PRIVATE)
        return prefs.getString("chatAppPrivateKey", null) != null && prefs.getString("chatAppPublicKey", null) != null
    }

    // ==========================================
    // 🔒 KEY DERIVATION & CACHING
    // ==========================================

    private fun importPrivateKey(base64Key: String): PrivateKey {
        val bytes = base64ToUint8(base64Key)
        val spec = PKCS8EncodedKeySpec(bytes)
        return KeyFactory.getInstance("EC").generatePrivate(spec)
    }

    private fun importPublicKey(base64Key: String): PublicKey {
        val bytes = base64ToUint8(base64Key)
        val spec = X509EncodedKeySpec(bytes)
        return KeyFactory.getInstance("EC").generatePublic(spec)
    }

    private fun getSharedKeyCacheKey(targetCode: String, myPrivKey: String, targetPubKey: String): String {
        val pubKeyHash = if (targetPubKey.length > 16) targetPubKey.takeLast(16) else "nopub"
        val privKeyHash = if (myPrivKey.length > 24) myPrivKey.takeLast(24) else "nokey"
        return "${targetCode}::${privKeyHash}::${pubKeyHash}"
    }

    private fun deriveSharedKey(myPrivKeyBase64: String, targetPubKeyBase64: String, cacheKeyStr: String): SecretKey {
        sharedKeyCache.get(cacheKeyStr)?.let { return it }

        val myPrivate = importPrivateKey(myPrivKeyBase64)
        val targetPublic = importPublicKey(targetPubKeyBase64)

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(myPrivate)
        keyAgreement.doPhase(targetPublic, true)

        val sharedSecret = keyAgreement.generateSecret()
        
        // JS WebCrypto deriveKey defaults to SHA-256 if used for AES-GCM without explicit KDF
        val aesKeyBytes = MessageDigest.getInstance("SHA-256").digest(sharedSecret)
        val secretKey = SecretKeySpec(aesKeyBytes, "AES")
        
        sharedKeyCache.put(cacheKeyStr, secretKey)
        return secretKey
    }

    // ==========================================
    // 🔒 TEXT ENCRYPTION
    // ==========================================

    fun encryptText(context: Context, text: String, targetCode: String, targetPubKeyBase64: String): String {
        if (text.isEmpty() || targetCode.isEmpty()) return text

        val prefs = context.getSharedPreferences("ChatArenaPrefs", Context.MODE_PRIVATE)
        val myPrivKey = prefs.getString("chatAppPrivateKey", null) ?: throw Exception("My private key missing")

        val cacheKey = getSharedKeyCacheKey(targetCode, myPrivKey, targetPubKeyBase64)
        val sharedKey = deriveSharedKey(myPrivKey, targetPubKeyBase64, cacheKey)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        cipher.init(Cipher.ENCRYPT_MODE, sharedKey, GCMParameterSpec(128, iv))

        val ciphertext = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        return "$E2E_PREFIX${uint8ToBase64(iv)}:${uint8ToBase64(ciphertext)}"
    }

    fun decryptText(context: Context, cipherText: String, targetCode: String, targetPubKeyBase64: String?): String {
        if (!cipherText.startsWith(E2E_PREFIX)) return cipherText
        if (targetPubKeyBase64 == null) return "🔒 Message Unavailable"

        val prefs = context.getSharedPreferences("ChatArenaPrefs", Context.MODE_PRIVATE)
        val myPrivKey = prefs.getString("chatAppPrivateKey", null) ?: return "🔒 Message Unavailable"

        try {
            val parts = cipherText.split(":")
            if (parts.size != 3) return "🔒 Message Unavailable"

            val iv = base64ToUint8(parts[1])
            val ciphertext = base64ToUint8(parts[2])

            val cacheKey = getSharedKeyCacheKey(targetCode, myPrivKey, targetPubKeyBase64)
            val sharedKey = deriveSharedKey(myPrivKey, targetPubKeyBase64, cacheKey)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, sharedKey, GCMParameterSpec(128, iv))
            
            return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed")
            return "🔒 Message Unavailable"
        }
    }

    // ==========================================
    // 🖼️ THUMBNAIL GENERATOR
    // ==========================================

    fun generateThumbnail(file: File, mimeType: String): String? {
        val maxDim = 200
        var bitmap: Bitmap? = null

        try {
            if (mimeType.startsWith("image/")) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, options)
                
                val scale = minOf(maxDim.toFloat() / options.outWidth, maxDim.toFloat() / options.outHeight, 1f)
                val reqWidth = (options.outWidth * scale).toInt().coerceAtLeast(1)
                val reqHeight = (options.outHeight * scale).toInt().coerceAtLeast(1)

                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight) }
                val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                bitmap = Bitmap.createScaledBitmap(decoded, reqWidth, reqHeight, true)

            } else if (mimeType.startsWith("video/")) {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(file.absolutePath)
                bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(100000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxDim, maxDim)
                } else {
                    retriever.getFrameAtTime(100000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
                retriever.release()
            }
        } catch (e: Exception) {
            return null
        }

        if (bitmap == null) return null

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
        val bmpBytes = outputStream.toByteArray()
        return "data:image/jpeg;base64," + uint8ToBase64(bmpBytes)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    // ==========================================
    // 🚀 CHUNKED MEDIA + CLOUDINARY
    // ==========================================

    private suspend fun getCloudinarySignature(token: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://chatarena.in/api/cloudinary-sign")
                .header("Authorization", "Bearer $token")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                return@withContext JSONObject(response.body?.string() ?: "{}")
            }
        } catch (e: Exception) { Log.e(TAG, "Sign fetch failed") }
        return@withContext null
    }

    private suspend fun uploadChunkToCloudinary(blobBytes: ByteArray, signData: JSONObject): String? = withContext(Dispatchers.IO) {
        try {
            val apiKey = signData.getString("api_key")
            val timestamp = signData.getString("timestamp")
            val signature = signData.getString("signature")
            val uploadPreset = signData.getString("upload_preset")

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "chunk.enc", blobBytes.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                .addFormDataPart("api_key", apiKey)
                .addFormDataPart("timestamp", timestamp)
                .addFormDataPart("signature", signature)
                .addFormDataPart("upload_preset", uploadPreset)
                .build()

            val request = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/$CLOUDINARY_CLOUD/raw/upload")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                val json = JSONObject(responseBody)
                if (json.has("secure_url")) return@withContext json.getString("secure_url")
            }
        } catch (e: Exception) { Log.e(TAG, "Cloudinary upload failed") }
        return@withContext null
    }

    data class MediaUploadResult(val urls: List<String>, val totalChunks: Int, val mime: String, val size: Long, val thumb: String?)

    suspend fun encryptAndUploadMedia(
        context: Context,
        file: File,
        mimeType: String,
        targetCode: String,
        targetPubKeyBase64: String,
        onProgress: (Float) -> Unit
    ): MediaUploadResult? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("ChatArenaPrefs", Context.MODE_PRIVATE)
        val myPrivKey = prefs.getString("chatAppPrivateKey", null) ?: return@withContext null
        val token = TokenManager.getToken(context) ?: return@withContext null

        try {
            val signData = getCloudinarySignature(token) ?: return@withContext null
            val cacheKey = getSharedKeyCacheKey(targetCode, myPrivKey, targetPubKeyBase64)
            val sharedKey = deriveSharedKey(myPrivKey, targetPubKeyBase64, cacheKey)

            // 1. Encrypt Thumbnail
            var encryptedThumb: String? = null
            generateThumbnail(file, mimeType)?.let { thumbBase64 ->
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val iv = ByteArray(12); SecureRandom().nextBytes(iv)
                cipher.init(Cipher.ENCRYPT_MODE, sharedKey, GCMParameterSpec(128, iv))
                val ciphertext = cipher.doFinal(thumbBase64.toByteArray(Charsets.UTF_8))
                encryptedThumb = "$E2E_PREFIX${uint8ToBase64(iv)}:${uint8ToBase64(ciphertext)}"
            }

            // 2. Encrypt & Upload Chunks
            // FIX: .toInt() add kiya gaya hai
            val totalChunks = maxOf(1, ((file.length() + MEDIA_CHUNK_SIZE - 1) / MEDIA_CHUNK_SIZE).toInt())
            val urls = mutableListOf<String>()

            for (i in 0 until totalChunks) {
                val start = i.toLong() * MEDIA_CHUNK_SIZE
                val end = minOf(start + MEDIA_CHUNK_SIZE.toLong(), file.length())
                val chunkBytes = ByteArray((end - start).toInt())
                
                file.inputStream().use { fis ->
                    fis.skip(start)
                    fis.read(chunkBytes)
                }

                // AAD: Uint32Array([i]) -> 4 bytes little-endian
                val aad = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(i).array()

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val iv = ByteArray(12); SecureRandom().nextBytes(iv)
                cipher.init(Cipher.ENCRYPT_MODE, sharedKey, GCMParameterSpec(128, iv))
                cipher.updateAAD(aad)
                
                val ciphertext = cipher.doFinal(chunkBytes)

                // Combined: IV (12) + Ciphertext
                val combined = ByteArray(iv.size + ciphertext.size)
                System.arraycopy(iv, 0, combined, 0, iv.size)
                System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)

                val url = uploadChunkToCloudinary(combined, signData) ?: return@withContext null
                urls.add(url)
                onProgress((i + 1).toFloat() / totalChunks)
            }

            return@withContext MediaUploadResult(urls, totalChunks, mimeType, file.length(), encryptedThumb)
        } catch (e: Exception) {
            Log.e(TAG, "Media upload failed: ${e.message}")
            return@withContext null
        }
    }

    suspend fun downloadAndDecryptMedia(
        context: Context,
        urls: List<String>,
        targetCode: String,
        targetPubKeyBase64: String
    ): ByteArray? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("ChatArenaPrefs", Context.MODE_PRIVATE)
        val myPrivKey = prefs.getString("chatAppPrivateKey", null) ?: return@withContext null

        try {
            val cacheKey = getSharedKeyCacheKey(targetCode, myPrivKey, targetPubKeyBase64)
            val sharedKey = deriveSharedKey(myPrivKey, targetPubKeyBase64, cacheKey)

            val outputStream = ByteArrayOutputStream()

            for (i in urls.indices) {
                val request = Request.Builder().url(urls[i]).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) throw Exception("Chunk download failed")

                val combined = response.body?.bytes() ?: throw Exception("Empty body")
                if (combined.size <= 12) throw Exception("Invalid chunk")

                val iv = combined.copyOfRange(0, 12)
                val ciphertext = combined.copyOfRange(12, combined.size)

                val aad = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(i).array()

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, sharedKey, GCMParameterSpec(128, iv))
                
                // FIX: "val cannot be reassigned" error solved by using try-catch as expression
                val plain = try {
                    cipher.updateAAD(aad)
                    cipher.doFinal(ciphertext)
                } catch (e: Exception) {
                    // Fallback without AAD (just in case)
                    val cipherFallback = Cipher.getInstance("AES/GCM/NoPadding")
                    cipherFallback.init(Cipher.DECRYPT_MODE, sharedKey, GCMParameterSpec(128, iv))
                    cipherFallback.doFinal(ciphertext)
                }
                
                outputStream.write(plain)
            }

            return@withContext outputStream.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Media download failed")
            return@withContext null
        }
    }
}
