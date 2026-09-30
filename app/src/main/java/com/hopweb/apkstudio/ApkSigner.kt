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
            ctx.assets.open("debug.keystore").use { input ->
                ksFile.outputStream().use { input.copyTo(it) }
            }
        }

        val ks = KeyStore.getInstance("JKS").apply {
            ksFile.inputStream().use { load(it, PASS.toCharArray()) }
        }
        val key = ks.getKey(ALIAS, PASS.toCharArray()) as PrivateKey
        val cert = ks.getCertificate(ALIAS) as X509Certificate

        ApkSigner.Builder(
            listOf(ApkSigner.SignerConfig.Builder(
                ALIAS, key, listOf(cert)
            ).build())
        )
            .setInputApk(inApk)
            .setOutputApk(outApk)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .build()
            .sign()
    }
}
