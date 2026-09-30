package com.hopweb.apkstudio

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hopweb.apkstudio.databinding.ActivityEditorBinding
import java.io.File

class EditorActivity : AppCompatActivity() {

    private lateinit var b: ActivityEditorBinding
    private lateinit var file: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(b.root)

        val path = intent.getStringExtra("path") ?: run { finish(); return }
        file = File(path)

        b.editor.apply {
            typefaceText = Typeface.MONOSPACE
            setText(file.readText())
        }

        b.btnSave.setOnClickListener { saveFile() }
        b.btnClose.setOnClickListener { finish() }
        b.btnFind.setOnClickListener { showFindReplace() }
        b.btnTheme.setOnClickListener { toggleTheme() }
    }

    private fun saveFile() {
        try {
            file.writeText(b.editor.text.toString())
            Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Fail: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showFindReplace() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 20, 30, 10)
        }
        val etFind = EditText(this).apply { hint = "Find" }
        val etReplace = EditText(this).apply { hint = "Replace with" }
        layout.addView(etFind); layout.addView(etReplace)

        AlertDialog.Builder(this)
            .setTitle("Find & Replace")
            .setView(layout)
            .setPositiveButton("Replace All") { _, _ ->
                val find = etFind.text.toString()
                val rep = etReplace.text.toString()
                if (find.isEmpty()) return@setPositiveButton
                val newText = b.editor.text.toString().replace(find, rep)
                b.editor.setText(newText)
                Toast.makeText(this, "Replaced!", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Find") { _, _ ->
                val find = etFind.text.toString()
                if (find.isEmpty()) return@setNeutralButton
                val txt = b.editor.text.toString()
                val idx = txt.indexOf(find)
                if (idx >= 0) {
                    b.editor.setSelection(idx, idx + find.length)
                    Toast.makeText(this, "Found at $idx", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(this, "Nahi mila", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private var darkTheme = true
    private fun toggleTheme() {
        darkTheme = !darkTheme
        if (darkTheme) {
            b.editor.setBackgroundColor(0xFF1E1E1E.toInt())
            b.editor.setTextColor(0xFFFFFFFF.toInt())
        } else {
            b.editor.setBackgroundColor(0xFFFFFFFF.toInt())
            b.editor.setTextColor(0xFF000000.toInt())
        }
    }
}
