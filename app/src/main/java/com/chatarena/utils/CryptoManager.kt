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
    private const val MEDIA_CHUNK_SIZE = 5 * 1024 * 1024
    private const val CLOUDINARY_CLOUD = "gr8tp1tg"
    private const val PREF_KEYS_CACHE = "ChatArenaKeysCache"

    private val publicKeyCache = object : LruCache<String, String>(200) {}
    private val sharedKeyCache = object : LruCache<String, SecretKey>(200) {}

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    private fun uint8ToBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun base64ToUint8(b64: String): ByteArray = Base64.decode(b64, Base64.NO_WRAP)

    // ==========================================
    // 🔒 KEY MANAGEMENT
    // ==========================================

    fun generateKeyPair(): Pair<String, String> {
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
        val keyPair = keyGen.generateKeyPair()
        return Pair(
            uint8ToBase64(keyPair.public.encoded),
            uint8ToBase64(keyPair.private.encoded)
        )
    }

    fun ensureE2EKeys(context: Context): String {
        val prefs = context.getSharedPreferences("ChatArenaPrefs", Context.MODE_PRIVATE)
        val currentUserCode = TokenManager.getUserCode(context) ?: ""
        val owner = prefs.getString("chatAppKeysOwner", "")

        var privKey = prefs.getString("chatAppPrivateKey", null)
        var pubKey = prefs.getString("chatAppPublicKey", null)

        if (privKey != null && pubKey != null && owner != currentUserCode) {
            privKey = null; pubKey = null
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

        // 🔥 FIX: RAM caches bhi clear karo, warna nayi keys ke saath purani shared keys use hongi
        sharedKeyCache.evictAll()
        publicKeyCache.evictAll()
        Log.d(TAG, "E2E keys and caches cleared")
    }

    fun hasLocalKeys(context: Context): Boolean {
        val prefs = context.getSharedPreferences("ChatArenaPrefs", Context.MODE_PRIVATE)
        return prefs.getString("chatAppPrivateKey", null) != null &&
               prefs.getString("chatAppPublicKey", null) != null
    }

    // ==========================================
    // 🚀 PUBLIC KEY DISK CACHE
    // ==========================================

    fun savePublicKeyToDisk(context: Context, targetCode: String, publicKey: String) {
        // 🔥 FIX: Agar key badal gayi (uninstall/reinstall), to purani shared key cache se hatao
        val oldKey = getPublicKeyFromDisk(context, targetCode)
        if (oldKey != null && oldKey != publicKey) {
            clearSharedKeyCacheForTarget(targetCode)
            Log.d(TAG, "Public key changed for $targetCode — shared cache invalidated")
        }

        publicKeyCache.put(targetCode, publicKey)
        val prefs = context.getSharedPreferences(PREF_KEYS_CACHE, Context.MODE_PRIVATE)
        prefs.edit().putString(targetCode, publicKey).apply()
    }

    fun getPublicKeyFromDisk(context: Context, targetCode: String): String? {
        publicKeyCache.get(targetCode)?.let { return it }
        val prefs = context.getSharedPreferences(PREF_KEYS_CACHE, Context.MODE_PRIVATE)
        val diskKey = prefs.getString(targetCode, null)
        if (diskKey != null) publicKeyCache.put(targetCode, diskKey)
        return diskKey
    }

    private fun clearSharedKeyCacheForTarget(targetCode: String) {
        val snapshot = sharedKeyCache.snapshot()
        for (key in snapshot.keys) {
            if (key.startsWith("$targetCode::")) sharedKeyCache.remove(key)
        }
    }

    // ==========================================
    // 🔒 KEY DERIVATION — FIXED
    // ==========================================

    private fun importPrivateKey(base64Key: String): PrivateKey =
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(base64ToUint8(base64Key)))

    private fun importPublicKey(base64Key: String): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(base64ToUint8(base64Key)))

    private fun getSharedKeyCacheKey(targetCode: String, myPrivKey: String, targetPubKey: String): String {
        val pubKeyHash = if (targetPubKey.length > 16) targetPubKey.takeLast(16) else "nopub"
        val privKeyHash = if (myPrivKey.length > 24) myPrivKey.takeLast(24) else "nokey"
        return "${targetCode}::${privKeyHash}::${pubKeyHash}"
    }

    // 🔥 FIX: RAW ECDH secret — JS WebCrypto ke saath match karta hai
    private fun deriveSharedKeyRaw(myPrivKeyBase64: String, targetPubKeyBase64: String): SecretKey {
        val myPrivate = importPrivateKey(myPrivKeyBase64)
        val targetPublic = importPublicKey(targetPubKeyBase64)
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(myPrivate)
        keyAgreement.doPhase(targetPublic, true)
        val sharedSecret = keyAgreement.generateSecret() // P-256 = 32 bytes
        return SecretKeySpec(sharedSecret, "AES")
    }

    // 🔥 FIX: Purane messages ke liye — SHA-256 fallback (jo pehle bane the)
    private fun deriveSharedKeyLegacy(myPrivKeyBase64: String, targetPubKeyBase64: String): SecretKey {
        val myPrivate = importPrivateKey(myPrivKeyBase64)
        val targetPublic = importPublicKey(targetPubKeyBase64)
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(myPrivate)
        keyAgreement.doPhase(targetPublic, true)
        val sharedSecret = keyAgreement.generateSecret()
        val aesKeyBytes = MessageDigest.getInstance("SHA-256").digest(sharedSecret)
        return SecretKeySpec(aesKeyBytes, "AES")
    }

    private fun getCachedSharedKey(cacheKey: String, useLegacy: Boolean, derive: () -> SecretKey): SecretKey {
        val fullKey = if (useLegacy) "$cacheKey::legacy" else cacheKey
        sharedKeyCache.get(fullKey)?.let { return it }
        val key = derive()
        sharedKeyCache.put(fullKey, key)
        return key
    }

    // ==========================================
    // 🔒 TEXT ENCRYPTION — RAW key (JS-compatible)
    // ==========================================

    fun encryptText(context: Context, text: String, targetCode: String, targetPubKeyBase64: String): String {
        if (text.isEmpty() || targetCode.isEmpty()) return text

        ensureE2EKeys(context) // 🔥 FIX: Auto-generate keys if missing (pehle throw ho raha tha)

        val prefs = context.getSharedPreferences("ChatArenaPrefs", Context.MODE_PRIVATE)
        val myPrivKey = prefs.getString("chatAppPrivateKey", null)
            ?: throw Exception("My private key missing even after ensure")

        val cacheKey = getSharedKeyCacheKey(targetCode, myPrivKey, targetPubKeyBase64)
        val sharedKey = getCachedSharedKey(cacheKey, false) {
            deriveSharedKeyRaw(myPrivKey, targetPubKeyBase64)
        }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12); SecureRandom().nextBytes(iv)
        cipher.init(Cipher.ENCRYPT_MODE, sharedKey, GCMParameterSpec(128, iv))

        val ciphertext = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        return "$E2E_PREFIX${uint8ToBase64(iv)}:${uint8ToBase64(ciphertext)}"
    }

    fun decryptText(context: Context, cipherText: String, targetCode: String, targetPubKeyBase64: String?): String {
        if (!cipherText.startsWith(E2E_PREFIX)) return cipherText
        if (targetPubKeyBase64 == null) {
            Log.e(TAG, "Decrypt failed: targetPubKeyBase64 is null for $targetCode")
            return "🔒 Message Unavailable"
        }

        val prefs = context.getSharedPreferences("ChatArenaPrefs", Context.MODE_PRIVATE)
        val myPrivKey = prefs.getString("chatAppPrivateKey", null)
        if (myPrivKey == null) {
            Log.e(TAG, "Decrypt failed: my private key is null")
            return "🔒 Message Unavailable"
        }

        val parts = cipherText.split(":")
        if (parts.size != 3) {
            Log.e(TAG, "Decrypt failed: malformed cipher (parts=${parts.size})")
            return "🔒 Message Unavailable"
        }

        return try {
            val iv = base64ToUint8(parts[1])
            val ciphertext = base64ToUint8(parts[2])
            val cacheKey = getSharedKeyCacheKey(targetCode, myPrivKey, targetPubKeyBase64)

            // 🔥 FIX 1: Pehle RAW key try karo (naya, cross-platform compatible)
            try {
                val sharedKey = getCachedSharedKey(cacheKey, false) {
                    deriveSharedKeyRaw(myPrivKey, targetPubKeyBase64)
                }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, sharedKey, GCMParameterSpec(128, iv))
                return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
            } catch (firstFail: Exception) {
                Log.w(TAG, "Raw decrypt failed, trying legacy (SHA-256) fallback: ${firstFail.message}")
            }

            // 🔥 FIX 2: Fallback — purane SHA-256 wale messages ke liye
            val legacyKey = getCachedSharedKey(cacheKey, true) {
                deriveSharedKeyLegacy(myPrivKey, targetPubKeyBase64)
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, legacyKey, GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Decrypt failed (both raw+legacy): ${e.javaClass.simpleName} — ${e.message}")
            "🔒 Message Unavailable"
        }
    }

    // ==========================================
    // 🖼️ THUMBNAIL (unchanged)
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
        } catch (e: Exception) { return null }
        if (bitmap == null) return null
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
        return "data:image/jpeg;base64," + uint8ToBase64(outputStream.toByteArray())
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
    // 🚀 CHUNKED MEDIA (Cloudinary)
    // ==========================================

    private suspend fun getCloudinarySignature(token: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://chatarena.in/api/cloudinary-sign")
                .header("Authorization", "Bearer $token")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) JSONObject(response.body?.string() ?: "{}") else null
        } catch (e: Exception) { Log.e(TAG, "Sign fetch failed"); null }
    }

    private suspend fun uploadChunkToCloudinary(blobBytes: ByteArray, signData: JSONObject): String? = withContext(Dispatchers.IO) {
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "chunk.enc", blobBytes.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                .addFormDataPart("api_key", signData.getString("api_key"))
                .addFormDataPart("timestamp", signData.getString("timestamp"))
                .addFormDataPart("signature", signData.getString("signature"))
                .addFormDataPart("upload_preset", signData.getString("upload_preset"))
                .build()
            val request = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/$CLOUDINARY_CLOUD/raw/upload")
                .post(requestBody).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                if (json.has("secure_url")) json.getString("secure_url") else null
            } else null
        } catch (e: Exception) { Log.e(TAG, "Cloudinary upload failed"); null }
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
        ensureE2EKeys(context)
        val prefs = context.getSharedPreferences("ChatArenaPrefs", Context.MODE_PRIVATE)
        val myPrivKey = prefs.getString("chatAppPrivateKey", null) ?: return@withContext null
        val token = TokenManager.getToken(context) ?: return@withContext null

        try {
            val signData = getCloudinarySignature(token) ?: return@withContext null
            val cacheKey = getSharedKeyCacheKey(targetCode, myPrivKey, targetPubKeyBase64)
            val sharedKey = getCachedSharedKey(cacheKey, false) {
                deriveSharedKeyRaw(myPrivKey, targetPubKeyBase64) // 🔥 FIX: RAW key
            }

            var encryptedThumb: String? = null
            generateThumbnail(file, mimeType)?.let { thumbBase64 ->
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val iv = ByteArray(12); SecureRandom().nextBytes(iv)
                cipher.init(Cipher.ENCRYPT_MODE, sharedKey, GCMParameterSpec(128, iv))
                val ct = cipher.doFinal(thumbBase64.toByteArray(Charsets.UTF_8))
                encryptedThumb = "$E2E_PREFIX${uint8ToBase64(iv)}:${uint8ToBase64(ct)}"
            }

            val totalChunks = maxOf(1, ((file.length() + MEDIA_CHUNK_SIZE - 1) / MEDIA_CHUNK_SIZE).toInt())
            val urls = mutableListOf<String>()

            for (i in 0 until totalChunks) {
                val start = i.toLong() * MEDIA_CHUNK_SIZE
                val end = minOf(start + MEDIA_CHUNK_SIZE.toLong(), file.length())
                val chunkBytes = ByteArray((end - start).toInt())
                file.inputStream().use { fis -> fis.skip(start); fis.read(chunkBytes) }

                val aad = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(i).array()
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val iv = ByteArray(12); SecureRandom().nextBytes(iv)
                cipher.init(Cipher.ENCRYPT_MODE, sharedKey, GCMParameterSpec(128, iv))
                cipher.updateAAD(aad)
                val ct = cipher.doFinal(chunkBytes)

                val combined = ByteArray(iv.size + ct.size)
                System.arraycopy(iv, 0, combined, 0, iv.size)
                System.arraycopy(ct, 0, combined, iv.size, ct.size)

                val url = uploadChunkToCloudinary(combined, signData) ?: return@withContext null
                urls.add(url)
                onProgress((i + 1).toFloat() / totalChunks)
            }

            MediaUploadResult(urls, totalChunks, mimeType, file.length(), encryptedThumb)
        } catch (e: Exception) {
            Log.e(TAG, "Media upload failed: ${e.message}")
            null
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
            val sharedKey = getCachedSharedKey(cacheKey, false) {
                deriveSharedKeyRaw(myPrivKey, targetPubKeyBase64) // 🔥 FIX: RAW key
            }

            val outputStream = ByteArrayOutputStream()
            for (i in urls.indices) {
                val response = client.newCall(Request.Builder().url(urls[i]).build()).execute()
                if (!response.isSuccessful) throw Exception("Chunk download failed")
                val combined = response.body?.bytes() ?: throw Exception("Empty body")
                if (combined.size <= 12) throw Exception("Invalid chunk")

                val iv = combined.copyOfRange(0, 12)
                val ciphertext = combined.copyOfRange(12, combined.size)
                val aad = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(i).array()

                val plain = try {
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, sharedKey, GCMParameterSpec(128, iv))
                    cipher.updateAAD(aad)
                    cipher.doFinal(ciphertext)
                } catch (e: Exception) {
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, sharedKey, GCMParameterSpec(128, iv))
                    cipher.doFinal(ciphertext)
                }
                outputStream.write(plain)
            }
            outputStream.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Media download failed: ${e.message}")
            null
        }
    }
}
