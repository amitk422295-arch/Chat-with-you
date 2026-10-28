package com.chatarena.utils

import android.content.Context
import android.provider.ContactsContract
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Local Contact ka data model
data class LocalContact(
    val name: String,
    val mobile: String
)

object ContactHelper {
    private const val TAG = "ContactHelper"

    /**
     * Phonebook se saare contacts fetch karta hai.
     * Ye function background thread (IO) par chalta hai taaki UI freeze na ho.
     */
    suspend fun getLocalContacts(context: Context): List<LocalContact> = withContext(Dispatchers.IO) {
        val contactsList = mutableListOf<LocalContact>()
        val seenNumbers = mutableSetOf<String>() // Duplicate numbers hatane ke liye

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )

            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (it.moveToNext()) {
                    val name = it.getString(nameIndex) ?: "Unknown"
                    val rawNumber = it.getString(numberIndex) ?: ""

                    // Number ko clean karo (sirf last 10 digits)
                    val cleanNumber = normalizeNumber(rawNumber)

                    // Agar valid 10-digit number hai aur pehle se list me nahi hai
                    if (cleanNumber.length == 10 && !seenNumbers.contains(cleanNumber)) {
                        seenNumbers.add(cleanNumber)
                        contactsList.add(LocalContact(name, cleanNumber))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching contacts: ${e.message}")
        }

        return@withContext contactsList
    }

    /**
     * Number se +91, spaces, dashes hata kar sirf last 10 digits nikalta hai.
     */
    private fun normalizeNumber(raw: String): String {
        val digits = raw.replace(Regex("\\D"), "")
        return if (digits.length > 10) digits.takeLast(10) else digits
    }
}
