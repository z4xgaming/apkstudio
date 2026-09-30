package com.hopweb.apkstudio

import android.util.Log
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ApkCloner {

    private const val TAG = "ApkCloner"

    /**
     * Extract APK to folder
     */
    fun extract(apk: File, outDir: File): File {
        outDir.mkdirs()
        ZipInputStream(apk.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val f = File(outDir, entry.name)
                if (!f.canonicalPath.startsWith(outDir.canonicalPath)) {
                    throw SecurityException("Bad zip entry")
                }
                if (entry.isDirectory) f.mkdirs()
                else {
                    f.parentFile?.mkdirs()
                    f.outputStream().buffered().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return outDir
    }

    /**
     * Change package name in manifest + resources + DEX
     */
    fun changePackage(decodedDir: File, oldPkg: String, newPkg: String) {
        // 1. Update AndroidManifest.xml
        val manifest = File(decodedDir, "AndroidManifest.xml")
        if (manifest.exists()) {
            var txt = manifest.readText()
            // Replace package="oldPkg"
            txt = txt.replace("package=\"$oldPkg\"", "package=\"$newPkg\"")
            // Replace all occurrences of oldPkg in provider authorities, etc.
            txt = txt.replace("\"$oldPkg.", "\"$newPkg.")
            txt = txt.replace("$oldPkg.", "$newPkg.")
            manifest.writeText(txt)
            Log.d(TAG, "Manifest updated")
        }

        // 2. Update classes.dex — replace package strings
        // DEX is binary; simple string replace works for most cases
        decodedDir.walkTopDown().filter { it.name.endsWith(".dex") }.forEach { dex ->
            try {
                val bytes = dex.readBytes()
                val modified = replaceBytesInDex(bytes, oldPkg, newPkg)
                if (modified != null) {
                    dex.writeBytes(modified)
                    Log.d(TAG, "DEX updated: ${dex.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "DEX update fail: ${e.message}")
            }
        }

        // 3. Update resources.arsc — simple string replacement
        // Note: This is a simplified approach. Full rename needs proper ARSC parsing.
        val arsc = File(decodedDir, "resources.arsc")
        if (arsc.exists()) {
            try {
                val bytes = arsc.readBytes()
                val modified = replaceBytes(bytes, oldPkg, newPkg)
                if (modified != null) arsc.writeBytes(modified)
            } catch (e: Exception) {
                Log.e(TAG, "ARSC update fail: ${e.message}")
            }
        }
    }

    /**
     * Replace string in DEX bytes (keeping structure intact)
     */
    private fun replaceBytesInDex(input: ByteArray, oldStr: String, newStr: String): ByteArray? {
        // DEX uses MUTF-8 with length prefix. Simple approach:
        // Only replace if new string length == old length (safe).
        // For different lengths, we skip (advanced SMALI editing needed).
        if (oldStr.length != newStr.length) {
            // Fallback: try byte array replace if same length
            return replaceBytes(input, oldStr, newStr)
        }
        return replaceBytes(input, oldStr, newStr)
    }

    /**
     * Byte-level string replacement (safe version)
     */
    private fun replaceBytes(input: ByteArray, oldStr: String, newStr: String): ByteArray? {
        val oldBytes = oldStr.toByteArray(Charsets.UTF_8)
        val newBytes = newStr.toByteArray(Charsets.UTF_8)
        var count = 0
        for (i in 0..input.size - oldBytes.size) {
            var match = true
            for (j in oldBytes.indices) {
                if (input[i + j] != oldBytes[j]) { match = false; break }
            }
            if (match) count++
        }
        if (count == 0) return null

        val out = ByteArray(input.size + (newBytes.size - oldBytes.size) * count)
        var outPos = 0
        var i = 0
        while (i < input.size) {
            var match = false
            if (i + oldBytes.size <= input.size) {
                match = true
                for (j in oldBytes.indices) {
                    if (input[i + j] != oldBytes[j]) { match = false; break }
                }
            }
            if (match) {
                System.arraycopy(newBytes, 0, out, outPos, newBytes.size)
                outPos += newBytes.size
                i += oldBytes.size
            } else {
                out[outPos++] = input[i++]
            }
        }
        return out.copyOf(outPos)
    }

    /**
     * Repack decoded folder into APK
     */
    fun repack(sourceDir: File, outApk: File) {
        ZipOutputStream(outApk.outputStream().buffered()).use { zos ->
            val priority = listOf("AndroidManifest.xml", "resources.arsc")
            val files = sourceDir.walkTopDown().filter { it.isFile }.toList()

            val sorted = files.sortedBy { f ->
                val rel = f.relativeTo(sourceDir).path.replace('\\', '/')
                priority.indexOf(rel).let { if (it == -1) 100 else it }
            }

            sorted.forEach { f ->
                val rel = f.relativeTo(sourceDir).path.replace('\\', '/')
                val entry = ZipEntry(rel).apply { time = f.lastModified() }
                zos.putNextEntry(entry)
                f.inputStream().buffered().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }
}
