package com.hopweb.apkstudio

import android.content.Context
import android.util.Log
import com.android.apksig.ApkSigner
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

object ApkSigner {

    private const val TAG = "ApkSigner"
    private const val ALIAS = "androiddebugkey"
    private const val PASS = "android"

    fun sign(ctx: Context, inApk: File, outApk: File) {
        val ksFile = File(ctx.filesDir, "debug.keystore")

        // Always copy fresh from assets (dont trust cache)
        try {
            ctx.assets.open("debug.keystore").use { input ->
                ksFile.outputStream().use { input.copyTo(it) }
            }
            Log.d(TAG, "Keystore copied: ${ksFile.length()} bytes")
        } catch (e: Exception) {
            throw Exception("Asset copy fail: ${e.message}")
        }

        if (ksFile.length() < 500) {
            throw Exception("Keystore too small (corrupt): ${ksFile.length()} bytes")
        }

        val ks = loadKeystore(ksFile)
        val key = ks.getKey(ALIAS, PASS.toCharArray()) as? PrivateKey
            ?: throw Exception("Private key nahi mili")
        val cert = ks.getCertificate(ALIAS) as? X509Certificate
            ?: throw Exception("Certificate nahi mili")

        ApkSigner.Builder(
            listOf(ApkSigner.SignerConfig.Builder(ALIAS, key, listOf(cert)).build())
        )
            .setInputApk(inApk)
            .setOutputApk(outApk)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()
            .sign()
    }

    private fun loadKeystore(file: File): KeyStore {
        val errors = StringBuilder()
        // Try PKCS12 first (best for Android)
        for (type in listOf("PKCS12", "JKS", "BKS", "AndroidKeyStore")) {
            try {
                val ks = KeyStore.getInstance(type)
                file.inputStream().use { ks.load(it, PASS.toCharArray()) }
                if (ks.containsAlias(ALIAS)) {
                    Log.d(TAG, "Loaded keystore type: $type")
                    return ks
                }
            } catch (e: Exception) {
                errors.append("$type: ${e.message}; ")
            }
        }
        throw Exception("Keystore load fail: $errors")
    }
}
