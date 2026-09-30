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
        val ksFile = File(ctx.filesDir, "debug.p12")

        try {
            ctx.assets.open("debug.p12").use { input ->
                ksFile.outputStream().use { input.copyTo(it) }
            }
        } catch (e: Exception) {
            throw Exception("Asset copy fail: ${e.message}")
        }

        if (ksFile.length() < 500) {
            throw Exception("Keystore too small: ${ksFile.length()} bytes")
        }

        val ks = KeyStore.getInstance("PKCS12").apply {
            ksFile.inputStream().use { load(it, PASS.toCharArray()) }
        }
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
}
