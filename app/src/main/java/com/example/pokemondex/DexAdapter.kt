package com.example.pokemondex

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DexAdapter(
    private val context: Context,
    private val onClick: (DexEntry) -> Unit
) : RecyclerView.Adapter<DexAdapter.DexViewHolder>() {

    private val items = ArrayList<DexEntry>()
    private val imageCache = object : LruCache<String, Bitmap>(maxMemory) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun submit(list: List<DexEntry>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DexViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dex, parent, false)
        val holder = DexViewHolder(view)
        view.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos in items.indices) onClick(items[pos])
        }
        return holder
    }

    override fun onBindViewHolder(holder: DexViewHolder, position: Int) {
        val entry = items[position]
        holder.noView.text = "NO.${entry.no}"
        holder.nameView.text = entry.name

        val bitmap = loadDexImage(entry.img)
        if (bitmap != null) {
            holder.avatarView.setImageBitmap(bitmap)
            holder.avatarView.visibility = View.VISIBLE
            holder.placeholderView.visibility = View.GONE
        } else {
            holder.avatarView.visibility = View.GONE
            holder.placeholderView.text = entry.name.take(1)
            holder.placeholderView.visibility = View.VISIBLE
        }

        bindAttr(holder.attrIcon1, holder.attrText1, entry.attr1)
        bindAttr(holder.attrIcon2, holder.attrText2, entry.attr2)

        val extra = StringBuilder()
        if (entry.species.isNotEmpty()) extra.append(entry.species)
        if (entry.egg.isNotEmpty()) {
            if (extra.isNotEmpty()) extra.append(" · ")
            extra.append(entry.egg)
        }
        holder.infoView.text = extra.toString()
    }

    override fun getItemCount(): Int = items.size

    private fun bindAttr(iconView: ImageView, textView: TextView, attr: String) {
        if (attr.isEmpty()) {
            iconView.visibility = View.GONE
            textView.visibility = View.GONE
            return
        }
        iconView.visibility = View.VISIBLE
        textView.visibility = View.VISIBLE
        textView.text = attr
        val icon = loadAttrIcon(attr)
        if (icon != null) {
            iconView.setImageBitmap(icon)
        } else {
            iconView.visibility = View.GONE
        }
    }

    private fun loadDexImage(name: String): Bitmap? {
        val cacheKey = "dex/$name"
        imageCache.get(cacheKey)?.let { return it }
        val bitmap = try {
            context.assets.open("img/dex/$name.webp").use { stream ->
                val bytes = stream.readBytes()
                decodeSampled(bytes, 160)
            }
        } catch (e: Exception) {
            null
        }
        if (bitmap != null) {
            imageCache.put(cacheKey, bitmap)
        }
        return bitmap
    }

    private fun loadAttrIcon(attr: String): Bitmap? {
        val cacheKey = "attr/$attr"
        imageCache.get(cacheKey)?.let { return it }
        val bitmap = try {
            context.assets.open("img/attrs/$attr.webp").use { stream ->
                val bytes = stream.readBytes()
                decodeSampled(bytes, 64)
            }
        } catch (e: Exception) {
            null
        }
        if (bitmap != null) {
            imageCache.put(cacheKey, bitmap)
        }
        return bitmap
    }

    private fun decodeSampled(bytes: ByteArray, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        val bigger = maxOf(bounds.outWidth, bounds.outHeight)
        while (bigger > 0 && bigger / (sample * 2) >= maxSide) {
            sample *= 2
        }
        val options = BitmapFactory.Options()
        options.inSampleSize = sample
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    class DexViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val noView: TextView = itemView.findViewById(R.id.tvDexNo)
        val nameView: TextView = itemView.findViewById(R.id.tvDexName)
        val placeholderView: TextView = itemView.findViewById(R.id.tvDexPlaceholder)
        val avatarView: ImageView = itemView.findViewById(R.id.ivDexAvatar)
        val attrIcon1: ImageView = itemView.findViewById(R.id.ivDexAttr1)
        val attrText1: TextView = itemView.findViewById(R.id.tvDexAttr1)
        val attrIcon2: ImageView = itemView.findViewById(R.id.ivDexAttr2)
        val attrText2: TextView = itemView.findViewById(R.id.tvDexAttr2)
        val infoView: TextView = itemView.findViewById(R.id.tvDexInfo)
    }

    companion object {
        private val maxMemory =
            (Runtime.getRuntime().maxMemory() / 16).toInt().coerceAtLeast(8 * 1024 * 1024)
    }
}
