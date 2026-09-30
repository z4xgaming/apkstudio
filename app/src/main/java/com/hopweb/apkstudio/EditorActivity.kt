package com.hopweb.apkstudio

import android.graphics.Typeface
import android.os.Bundle
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

        b.btnSave.setOnClickListener {
            try {
                file.writeText(b.editor.text.toString())
                Toast.makeText(this, "Save ho gaya", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
            }
        }

        b.btnClose.setOnClickListener { finish() }
    }
}
