package com.hopweb.apkstudio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.hopweb.apkstudio.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var workspace: File? = null
    private var currentPath: File? = null
    private var apkName: String = "edited"

    private val pickApk = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res -> res.data?.data?.let { uri -> extractApk(uri) } }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) pickApkFile()
        else toast("Permission chahiye bhai!")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnPick.setOnClickListener { checkPermAndPick() }
        b.btnRepack.setOnClickListener { repackApk() }
        b.btnInstall.setOnClickListener { installApk() }
        b.btnBack.setOnClickListener { goUp() }

        setStatus("APK select karo shuru karne ke liye")
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
            if (perms.all {
                    ContextCompat.checkSelfPermission(this, it) ==
                            PackageManager.PERMISSION_GRANTED
                }) pickApkFile()
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

    private fun extractApk(uri: Uri) {
        showProgress(true)
        setStatus("Extract ho raha hai...")
        scope.launch {
            try {
                val outDir = File(filesDir, "workspace").apply {
                    deleteRecursively(); mkdirs()
                }
                ApkExtractor.extract(contentResolver, uri, outDir)
                workspace = outDir
                currentPath = outDir

                val name = queryName(uri) ?: "edited"
                apkName = name.removeSuffix(".apk") + "_edited"

                withContext(Dispatchers.Main) {
                    showProgress(false)
                    setStatus("Extract complete")
                    toast("Ab edit kar bhai!")
                    showFiles(outDir)
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

    private fun queryName(uri: Uri): String? = try {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
        }
    } catch (e: Exception) { null }

    private fun showFiles(dir: File) {
        val files = dir.listFiles()?.sortedWith(
            compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() }
        ) ?: return

        currentPath = dir
        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = FileAdapter(files) { f ->
            if (f.isDirectory) showFiles(f)
            else {
                val i = Intent(this, EditorActivity::class.java)
                i.putExtra("path", f.absolutePath)
                startActivity(i)
            }
        }
    }

    private fun goUp() {
        val cur = currentPath ?: return
        val ws = workspace ?: return
        if (cur.absolutePath == ws.absolutePath) {
            toast("Ye root hai"); return
        }
        showFiles(cur.parentFile ?: ws)
    }

    private fun repackApk() {
        val ws = workspace ?: return toast("Pehle APK extract kar")
        showProgress(true)
        setStatus("Repack + Sign ho raha hai...")
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
                    showProgress(false)
                    setStatus("Ready: ${signed.name}")
                    toast("APK ready!")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showProgress(false)
                    setStatus("Repack fail: ${e.message}")
                    toast("Fail: ${e.message}")
                }
            }
        }
    }

    private fun installApk() {
        val outDir = File(getExternalFilesDir(null), "output")
        val apk = outDir.listFiles()?.firstOrNull { it.name.endsWith("-signed.apk") }
        if (apk == null) return toast("Pehle Repack kar bhai!")

        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, "$packageName.fileprovider", apk
            )
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

    private fun showProgress(show: Boolean) {
        b.progress.visibility = if (show) View.VISIBLE else View.GONE
        b.btnPick.isEnabled = !show
        b.btnRepack.isEnabled = !show
        b.btnInstall.isEnabled = !show
    }

    private fun setStatus(msg: String) { b.tvStatus.text = msg }
    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy(); scope.cancel()
    }
}
