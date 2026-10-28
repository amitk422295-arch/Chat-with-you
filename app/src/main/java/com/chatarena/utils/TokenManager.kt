package com.chatarena.utils

import android.content.Context
import android.content.SharedPreferences

object TokenManager {

    private const val PREF_NAME = "ChatArenaPrefs"
    private const val KEY_TOKEN = "chat_token"
    private const val KEY_USER_CODE = "user_code"
    private const val KEY_EMAIL = "user_email"
    private const val KEY_FULL_NAME = "user_full_name"

    // SharedPreferences ka instance lene ke liye
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // Login hone par data save karne ke liye
    fun saveUserData(context: Context, token: String, userCode: String, email: String, fullName: String) {
        val editor = getPrefs(context).edit()
        editor.putString(KEY_TOKEN, token)
        editor.putString(KEY_USER_CODE, userCode)
        editor.putString(KEY_EMAIL, email)
        editor.putString(KEY_FULL_NAME, fullName)
        editor.apply()
    }

    // Token check karne ke liye (App start hote hi)
    fun getToken(context: Context): String? {
        return getPrefs(context).getString(KEY_TOKEN, null)
    }

    // Baaki user details nikalne ke liye
    fun getUserCode(context: Context): String? {
        return getPrefs(context).getString(KEY_USER_CODE, null)
    }

    fun getEmail(context: Context): String? {
        return getPrefs(context).getString(KEY_EMAIL, null)
    }

    fun getFullName(context: Context): String? {
        return getPrefs(context).getString(KEY_FULL_NAME, null)
    }

    // User login hai ya nahi check karne ke liye
    fun isLoggedIn(context: Context): Boolean {
        val token = getToken(context)
        return !token.isNullOrEmpty()
    }

    // Logout karne ke liye (Sab data delete)
    fun logout(context: Context) {
        val editor = getPrefs(context).edit()
        editor.clear()
        editor.apply()
    }
}
