package com.boatspark.app.core.auth

import android.annotation.SuppressLint
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EncryptedAuthStateStore(context: Context) {
    private val preferences = context.getSharedPreferences("encrypted_auth_state", Context.MODE_PRIVATE)

    @Volatile
    private var cachedKey: SecretKey? = null

    fun read(): String? {
        val encoded = preferences.getString(KEY_STATE, null) ?: return null
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        require(bytes.size > IV_SIZE) { "Stored authentication state is invalid" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(TAG_BITS, bytes.copyOfRange(0, IV_SIZE)),
        )
        return cipher.doFinal(bytes.copyOfRange(IV_SIZE, bytes.size)).decodeToString()
    }

    @SuppressLint("ApplySharedPref")
    fun write(value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.iv + cipher.doFinal(value.encodeToByteArray())
        // Synchronous so a rotated refresh token is on disk before the process can die.
        preferences.edit(commit = true) {
            putString(KEY_STATE, Base64.encodeToString(encrypted, Base64.NO_WRAP))
        }
    }

    @SuppressLint("ApplySharedPref")
    fun clear() {
        // Synchronous so a sign-out cannot be lost if the process dies right after.
        preferences.edit(commit = true) {
            remove(KEY_STATE)
        }
    }

    fun requiresLoginPrompt(): Boolean = preferences.getBoolean(KEY_PROMPT_LOGIN, false)

    fun setRequiresLoginPrompt(required: Boolean) {
        preferences.edit { putBoolean(KEY_PROMPT_LOGIN, required) }
    }

    private fun getOrCreateKey(): SecretKey = cachedKey ?: loadOrCreateKey().also { cachedKey = it }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "boatspark_oidc_state"
        const val KEY_STATE = "auth_state"
        const val KEY_PROMPT_LOGIN = "prompt_login"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val IV_SIZE = 12
    }
}

