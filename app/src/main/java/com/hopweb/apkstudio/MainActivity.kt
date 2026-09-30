package com.hopweb.apkstudio

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.hopweb.apkstudio.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var workspace: File? = null
    private var currentPath: File? = null
    private var apkName: String = "edited"
    private var allFiles: List<File> = emptyList()
    private var searchQuery: String = ""

    private val pickApk = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res -> res.data?.data?.let { uri -> extractApk(uri) } }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) pickApkFile()
        else toast("Permission chahiye!")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnPick.setOnClickListener { checkPermAndPick() }
        b.btnRepack.setOnClickListener { repackApk() }
        b.btnInstall.setOnClickListener { installApk() }
        b.btnBack.setOnClickListener { goUp() }
        b.btnInfo.setOnClickListener { showApkInfo() }
        b.btnNewFile.setOnClickListener { createNewFile() }

        b.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s.toString().lowercase()
                filterFiles()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
        })

        setStatus("APK select karo shuru karne ke liye")
    }

    private fun filterFiles() {
        val filtered = if (searchQuery.isEmpty()) allFiles
        else allFiles.filter { it.name.lowercase().contains(searchQuery) }
        b.recycler.adapter = FileAdapter(filtered,
            onClick = { f -> openFile(f) },
            onLongClick = { f -> showFileMenu(f) }
        )
    }

    private fun checkPermAndPick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")))
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
                return
            }
            pickApkFile()
        } else {
            val perms = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            if (perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED })
                pickApkFile()
            else permLauncher.launch(perms)
        }
    }

    private fun pickApkFile() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
        }
        pickApk.launch(i)
    }

    private fun extractApk(uri: Uri) {
        showProgress(true); setStatus("Extract ho raha hai...")
        scope.launch {
            try {
                val outDir = File(filesDir, "workspace").apply { deleteRecursively(); mkdirs() }
                ApkExtractor.extract(contentResolver, uri, outDir)
                workspace = outDir; currentPath = outDir
                val name = queryName(uri) ?: "edited"
                apkName = name.removeSuffix(".apk") + "_edited"
                withContext(Dispatchers.Main) {
                    showProgress(false); setStatus("Extract complete: $apkName")
                    toast("Edit kar bhai!"); showFiles(outDir)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showProgress(false); setStatus("Error: ${e.message}")
                    toast("Fail: ${e.message}")
                }
            }
        }
    }

    private fun queryName(uri: Uri): String? = try {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
        }
    } catch (e: Exception) { null }

    private fun showFiles(dir: File) {
        currentPath = dir
        allFiles = dir.listFiles()?.sortedWith(
            compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() }
        ) ?: emptyList()
        b.recycler.layoutManager = LinearLayoutManager(this)
        filterFiles()
        setStatus("${allFiles.size} items - ${dir.name}")
    }

    private fun openFile(f: File) {
        if (f.isDirectory) showFiles(f)
        else {
            val i = Intent(this, EditorActivity::class.java)
            i.putExtra("path", f.absolutePath)
            startActivity(i)
        }
    }

    private fun showFileMenu(f: File) {
        val options = arrayOf("📋 Info", "✏️ Rename", "🗑️ Delete", "📤 Share")
        AlertDialog.Builder(this)
            .setTitle(f.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showFileInfo(f)
                    1 -> renameFile(f)
                    2 -> confirmDelete(f)
                    3 -> shareFile(f)
                }
            }
            .show()
    }

    private fun showFileInfo(f: File) {
        val date = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            .format(Date(f.lastModified()))
        val size = formatSize(f.length())
        val type = if (f.isDirectory) "Folder" else (f.extension.ifEmpty { "file" })
        val msg = """
            Name: ${f.name}
            Type: $type
            Size: $size
            Modified: $date
            Path: ${f.absolutePath}
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("📋 File Info")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun renameFile(f: File) {
        val input = EditText(this).apply { setText(f.name) }
        AlertDialog.Builder(this)
            .setTitle("✏️ Rename")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty()) return@setPositiveButton toast("Naam khaali nahi")
                val newFile = File(f.parentFile, newName)
                if (f.renameTo(newFile)) {
                    toast("Renamed!"); showFiles(currentPath!!)
                } else toast("Rename fail")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(f: File) {
        AlertDialog.Builder(this)
            .setTitle("🗑️ Delete?")
            .setMessage("${f.name} delete karna hai?")
            .setPositiveButton("Delete") { _, _ ->
                val ok = if (f.isDirectory) f.deleteRecursively() else f.delete()
                if (ok) { toast("Deleted!"); showFiles(currentPath!!) }
                else toast("Delete fail")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareFile(f: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(i, "Share via"))
        } catch (e: Exception) { toast("Share fail: ${e.message}") }
    }

    private fun createNewFile() {
        val cur = currentPath ?: return toast("Pehle APK extract kar")
        val input = EditText(this).apply { hint = "filename.txt" }
        AlertDialog.Builder(this)
            .setTitle("➕ New File")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton toast("Naam daalo")
                val nf = File(cur, name)
                try {
                    if (nf.parentFile?.exists() == false) nf.parentFile?.mkdirs()
                    nf.createNewFile(); toast("Created!"); showFiles(cur)
                } catch (e: Exception) { toast("Fail: ${e.message}") }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showApkInfo() {
        val ws = workspace ?: return toast("Pehle APK extract kar")
        scope.launch {
            val manifest = File(ws, "AndroidManifest.xml")
            val info = if (manifest.exists()) {
                val txt = manifest.readText().take(3000)
                val pkg = Regex("package=\"([^\"]+)\"").find(txt)?.groupValues?.get(1) ?: "?"
                val ver = Regex("versionName=\"([^\"]+)\"").find(txt)?.groupValues?.get(1) ?: "?"
                val code = Regex("versionCode=\"([^\"]+)\"").find(txt)?.groupValues?.get(1) ?: "?"
                val perms = Regex("<uses-permission[^>]*android:name=\"([^\"]+)\"")
                    .findAll(txt).map { it.groupValues[1].substringAfterLast(".") }.toList()
                val size = ws.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                "Package: $pkg\nVersion: $ver ($code)\nSize: ${formatSize(size)}\n\nPermissions (${perms.size}):\n${perms.take(15).joinToString("\n")}"
            } else "Manifest nahi mila"
            withContext(Dispatchers.Main) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("📱 APK Info")
                    .setMessage(info)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun goUp() {
        val cur = currentPath ?: return; val ws = workspace ?: return
        if (cur.absolutePath == ws.absolutePath) return toast("Ye root hai")
        showFiles(cur.parentFile ?: ws)
    }

    private fun repackApk() {
        val ws = workspace ?: return toast("Pehle APK extract kar")
        showProgress(true); setStatus("Repack + Sign...")
        scope.launch {
            try {
                val outDir = File(getExternalFilesDir(null), "output").apply { mkdirs() }
                val unsigned = File(outDir, "$apkName-unsigned.apk")
                val signed = File(outDir, "$apkName-signed.apk")
                if (unsigned.exists()) unsigned.delete()
                if (signed.exists()) signed.delete()
                ApkPacker.pack(ws, unsigned)
                ApkSigner.sign(this@MainActivity, unsigned, signed)
                withContext(Dispatchers.Main) {
                    showProgress(false); setStatus("Ready: ${signed.name}")
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("✅ Repack Done")
                        .setMessage("APK ready!\n${signed.name}")
                        .setPositiveButton("Install") { _, _ -> installApk() }
                        .setNeutralButton("Share") { _, _ -> shareFile(signed) }
                        .setNegativeButton("Close", null)
                        .show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showProgress(false); setStatus("Fail: ${e.message}")
                    toast("Repack fail: ${e.message}")
                }
            }
        }
    }

    private fun installApk() {
        val outDir = File(getExternalFilesDir(null), "output")
        val apk = outDir.listFiles()?.firstOrNull { it.name.endsWith("-signed.apk") }
        if (apk == null) return toast("Pehle Repack kar!")
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apk)
            val i = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(i)
        } catch (e: Exception) { toast("Install fail: ${e.message}") }
    }

    private fun showProgress(show: Boolean) {
        b.progress.visibility = if (show) View.VISIBLE else View.GONE
        b.btnPick.isEnabled = !show; b.btnRepack.isEnabled = !show
        b.btnInstall.isEnabled = !show
    }

    private fun setStatus(msg: String) { b.tvStatus.text = msg }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
