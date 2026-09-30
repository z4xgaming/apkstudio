package com.hopweb.apkstudio

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class FileAdapter(
    private val items: List<File>,
    private val onClick: (File) -> Unit
) : RecyclerView.Adapter<FileAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tv: TextView = v.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
        val v = LayoutInflater.from(p.context)
            .inflate(android.R.layout.simple_list_item_1, p, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, i: Int) {
        val f = items[i]
        val icon = when {
            f.isDirectory -> "[DIR] "
            f.extension == "xml" -> "[XML] "
            f.extension == "dex" -> "[DEX] "
            f.extension == "png" || f.extension == "jpg" -> "[IMG] "
            f.extension == "smali" -> "[SML] "
            f.extension == "arsc" -> "[ARC] "
            else -> "[F] "
        }
        val size = if (f.isFile) " (${formatSize(f.length())})" else ""
        h.tv.text = "$icon${f.name}$size"
        h.tv.setTextColor(0xFFFFFFFF.toInt())
        h.itemView.setBackgroundColor(0xFF0D0D0D.toInt())
        h.itemView.setOnClickListener { onClick(f) }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }

    override fun getItemCount() = items.size
}
