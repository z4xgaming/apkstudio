package com.hopweb.apkstudio

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ApkPacker {
    fun pack(sourceDir: File, outApk: File) {
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
