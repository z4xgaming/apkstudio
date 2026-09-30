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
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.hopweb.apkstudio.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var sourceApk: File? = null
    private var originalPackage: String = ""
    private var originalAppName: String = ""
    private var clonedApkPath: File? = null

    private val pickApk = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        res.data?.data?.let { uri -> copyApkToWorkspace(uri) }
    }

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
        b.btnClone.setOnClickListener { showCloneDialog() }
        b.btnInstall.setOnClickListener { installClonedApk() }
        b.btnShare.setOnClickListener { shareClonedApk() }

        setStatus("APK select karo clone karne ke liye")
    }

    private fun checkPermAndPick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    startActivity(Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
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
            if (perms.all { ContextCompat.checkSelfPermission(this, it) ==
                    PackageManager.PERMISSION_GRANTED }) pickApkFile()
            else permLauncher.launch(perms)
        }
    }

    private fun pickApkFile() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        pickApk.launch(i)
    }

    private fun copyApkToWorkspace(uri: Uri) {
        showProgress(true)
        setStatus("APK copy ho raha hai...")
        scope.launch {
            try {
                val workDir = File(filesDir, "clone").apply { deleteRecursively(); mkdirs() }
                val src = File(workDir, "source.apk")
                contentResolver.openInputStream(uri)?.use { input ->
                    src.outputStream().use { input.copyTo(it) }
                }
                sourceApk = src

                // Extract package info
                val info = extractPackageInfo(src)
                originalPackage = info.first
                originalAppName = info.second

                withContext(Dispatchers.Main) {
                    showProgress(false)
                    setStatus("Ready: $originalPackage")
                    b.tvInfo.text = "📦 ${originalAppName}\n📛 ${originalPackage}\n💾 ${formatSize(src.length())}"
                    b.tvInfo.visibility = View.VISIBLE
                    b.btnClone.isEnabled = true
                    toast("Ab Clone dabao")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showProgress(false)
                    setStatus("Error: ${e.message}")
                    toast("Fail: ${e.message}")
                }
            }
        }
    }

    private fun extractPackageInfo(apk: File): Pair<String, String> {
        try {
            val pm = packageManager
            val info = pm.getPackageArchiveInfo(apk.absolutePath, 0)
            if (info != null) {
                val pkg = info.packageName ?: "unknown"
                val name = info.applicationInfo?.loadLabel(pm)?.toString() ?: "Unknown"
                return Pair(pkg, name)
            }
        } catch (_: Exception) {}
        return Pair("com.unknown.app", "Unknown App")
    }

    private fun showCloneDialog() {
        val src = sourceApk ?: return toast("Pehle APK pick kar")

        // Default suggestions
        val defaultPkg = originalPackage + ".clone"
        val defaultName = originalAppName + " Clone"

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
        }

        val lbl1 = TextView(this).apply { text = "Naya App Naam:"; setTextColor(0xFF000000.toInt()) }
        val etName = EditText(this).apply { setText(defaultName) }

        val lbl2 = TextView(this).apply { text = "Naya Package ID:"; setTextColor(0xFF000000.toInt()) }
        val etPkg = EditText(this).apply { setText(defaultPkg) }

        val lbl3 = TextView(this).apply {
            text = "\n⚠️ Ye original app ke saath install hoga. Fresh data hoga — dobara login karna padega."
            setTextColor(0xFF666666.toInt())
            textSize = 12f
        }

        layout.addView(lbl1); layout.addView(etName)
        layout.addView(lbl2); layout.addView(etPkg)
        layout.addView(lbl3)

        AlertDialog.Builder(this)
            .setTitle("🎭 Clone APK")
            .setView(layout)
            .setPositiveButton("Clone") { _, _ ->
                val newName = etName.text.toString().trim()
                val newPkg = etPkg.text.toString().trim()
                if (newName.isEmpty() || newPkg.isEmpty()) {
                    toast("Naam aur package zaroori hain")
                    return@setPositiveButton
                }
                if (!newPkg.contains(".")) {
                    toast("Package me kam se kam ek dot hona chahiye")
                    return@setPositiveButton
                }
                cloneApk(src, newPkg, newName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun cloneApk(src: File, newPackage: String, newAppName: String) {
        showProgress(true)
        setStatus("Clone ho raha hai... (2-3 min lag sakte hain)")

        scope.launch {
            try {
                val workDir = File(filesDir, "clone")
                val extractDir = File(workDir, "extract").apply { deleteRecursively(); mkdirs() }
                val outDir = File(getExternalFilesDir(null), "clones").apply { mkdirs() }
                val outApk = File(outDir, "${newAppName.replace(" ", "_")}_clone.apk")
                if (outApk.exists()) outApk.delete()

                // STEP 1: Extract APK using APKEditor
                withContext(Dispatchers.Main) { setStatus("Step 1/4: Extract...") }
                val decodedDir = ApkCloner.extract(src, extractDir)

                // STEP 2: Modify manifest + resources
                withContext(Dispatchers.Main) { setStatus("Step 2/4: Package ID change...") }
                ApkCloner.changePackage(decodedDir, originalPackage, newPackage)

                // STEP 3: Repack
                withContext(Dispatchers.Main) { setStatus("Step 3/4: Repack...") }
                val unsignedApk = File(workDir, "unsigned.apk")
                if (unsignedApk.exists()) unsignedApk.delete()
                ApkCloner.repack(decodedDir, unsignedApk)

                // STEP 4: Sign
                withContext(Dispatchers.Main) { setStatus("Step 4/4: Sign...") }
                ApkSigner.sign(this@MainActivity, unsignedApk, outApk)

                clonedApkPath = outApk

                withContext(Dispatchers.Main) {
                    showProgress(false)
                    setStatus("✅ Clone ready: ${outApk.name}")
                    b.btnInstall.isEnabled = true
                    b.btnShare.isEnabled = true

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("🎉 Clone Ready!")
                        .setMessage("""
                            App: $newAppName
                            Package: $newPackage
                            Size: ${formatSize(outApk.length())}

                            Original ke saath install hoga.
                            Fresh data — naya login karna padega.
                        """.trimIndent())
                        .setPositiveButton("Install") { _, _ -> installClonedApk() }
                        .setNegativeButton("Close", null)
                        .show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    showProgress(false)
                    setStatus("❌ Clone fail: ${e.message}")
                    toast("Fail: ${e.message}")
                }
            }
        }
    }

    private fun installClonedApk() {
        val apk = clonedApkPath ?: return toast("Pehle clone karo!")
        if (!apk.exists()) return toast("Clone file nahi mili")
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apk)
            val i = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(i)
        } catch (e: Exception) {
            toast("Install fail: ${e.message}")
        }
    }

    private fun shareClonedApk() {
        val apk = clonedApkPath ?: return toast("Pehle clone karo!")
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apk)
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(i, "Share APK"))
        } catch (e: Exception) { toast("Share fail: ${e.message}") }
    }

    private fun showProgress(show: Boolean) {
        b.progress.visibility = if (show) View.VISIBLE else View.GONE
        b.btnPick.isEnabled = !show
        b.btnClone.isEnabled = !show && sourceApk != null
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
