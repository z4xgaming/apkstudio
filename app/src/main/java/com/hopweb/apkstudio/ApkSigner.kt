package com.hopweb.apkstudio

import android.content.Context
import com.android.apksig.ApkSigner
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

object ApkSigner {

    private const val ALIAS = "androiddebugkey"
    private const val PASS = "android"

    fun sign(ctx: Context, inApk: File, outApk: File) {
        val ksFile = File(ctx.filesDir, "debug.keystore")
        if (!ksFile.exists()) {
            // Copy from assets
            try {
                ctx.assets.open("debug.keystore").use { input ->
                    ksFile.outputStream().use { input.copyTo(it) }
                }
            } catch (e: Exception) {
                throw Exception("Keystore asset nahi mila: ${e.message}")
            }
        }
        if (!ksFile.exists() || ksFile.length() == 0L) {
            throw Exception("Keystore empty hai")
        }

        // Auto-detect keystore format
        val ks = loadKeystore(ksFile)

        val key = ks.getKey(ALIAS, PASS.toCharArray()) as? PrivateKey
            ?: throw Exception("Private key nahi mili (alias: $ALIAS)")
        val cert = ks.getCertificate(ALIAS) as? X509Certificate
            ?: throw Exception("Certificate nahi mili")

        ApkSigner.Builder(
            listOf(ApkSigner.SignerConfig.Builder(
                ALIAS, key, listOf(cert)
            ).build())
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
        val types = listOf("PKCS12", "JKS", "BKS")
        for (type in types) {
            try {
                val ks = KeyStore.getInstance(type)
                file.inputStream().use { ks.load(it, PASS.toCharArray()) }
                // Verify key exists
                if (ks.containsAlias(ALIAS)) return ks
            } catch (e: Exception) {
                // Try next type
            }
        }
        throw Exception("Keystore load nahi hui (tried PKCS12/JKS/BKS)")
    }
}
