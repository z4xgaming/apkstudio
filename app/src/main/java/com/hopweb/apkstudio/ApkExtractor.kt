package com.hopweb.apkstudio

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.util.zip.ZipInputStream

object ApkExtractor {
    fun extract(resolver: ContentResolver, uri: Uri, outDir: File) {
        resolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zis ->
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
        } ?: throw Exception("File open nahi hui")
    }
}
