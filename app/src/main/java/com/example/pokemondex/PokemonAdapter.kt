package com.example.pokemondex

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PokemonAdapter(
    private val context: Context,
    private val onUnlockChanged: (name: String, checked: Boolean) -> Unit
) : RecyclerView.Adapter<PokemonAdapter.PokemonViewHolder>() {

    var unlockedProvider: ((name: String) -> Boolean)? = null
    var unlockMode: Boolean = false

    private var season: String = ""
    private var visiblePokemon: List<Pokemon> = emptyList()

    private val imageCache = object : LruCache<String, Bitmap>(maxMemory) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun setData(season: String, pokemonList: List<Pokemon>, type: String) {
        this.season = season
        visiblePokemon = pokemonList.filter { it.type == type }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PokemonViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pokemon, parent, false)
        val holder = PokemonViewHolder(view)
        view.setOnClickListener {
            if (!unlockMode) return@setOnClickListener
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            holder.checkView.performClick()
        }
        return holder
    }

    override fun onBindViewHolder(holder: PokemonViewHolder, position: Int) {
        val pokemon = visiblePokemon[position]
        holder.nameView.text = pokemon.name

        val unlocked = unlockedProvider?.invoke(pokemon.name) ?: false
        bindAvatar(holder, pokemon, unlocked)

        holder.categoryView.text = pokemon.category
        if (pokemon.category.isNotEmpty()) {
            holder.categoryView.visibility = View.VISIBLE
            holder.categoryView.setTextColor(categoryColor(pokemon.category))
        } else {
            holder.categoryView.visibility = View.GONE
        }

        bindAttr(holder.attrIcon1, holder.attrText1, pokemon.attr1)
        bindAttr(holder.attrIcon2, holder.attrText2, pokemon.attr2)

        holder.checkView.setOnCheckedChangeListener(null)
        holder.checkView.isChecked = unlocked
        holder.checkView.isEnabled = unlockMode
        holder.checkView.setOnCheckedChangeListener { _, checked ->
            if (checked != unlocked) {
                bindAvatar(holder, pokemon, checked)
            }
            onUnlockChanged(pokemon.name, checked)
        }
    }

    private fun bindAvatar(holder: PokemonViewHolder, pokemon: Pokemon, unlocked: Boolean) {
        if (pokemon.img.isNotEmpty()) {
            val bitmap = loadDexImage(pokemon.img)
            if (bitmap != null) {
                holder.avatarView.setImageBitmap(bitmap)
                holder.avatarView.visibility = View.VISIBLE
                holder.placeholderView.visibility = View.GONE
            } else {
                showPlaceholder(holder, pokemon)
            }
            return
        }
        val normalPath = "$season/${pokemon.name}"
        val shinyPath = "$season/${pokemon.name}_异色"
        val primary = if (unlocked) shinyPath else normalPath
        val secondary = if (unlocked) normalPath else shinyPath
        var bitmap = loadBitmap(primary)
        if (bitmap == null) {
            bitmap = loadBitmap(secondary)
        }
        if (bitmap != null) {
            holder.avatarView.setImageBitmap(bitmap)
            holder.avatarView.visibility = View.VISIBLE
            holder.placeholderView.visibility = View.GONE
        } else {
            showPlaceholder(holder, pokemon)
        }
    }

    private fun showPlaceholder(holder: PokemonViewHolder, pokemon: Pokemon) {
        holder.avatarView.visibility = View.GONE
        holder.placeholderView.text = pokemon.name.take(1)
        holder.placeholderView.visibility = View.VISIBLE
    }

    override fun getItemCount(): Int = visiblePokemon.size

    private fun bindAttr(iconView: ImageView, textView: TextView, attr: String) {
        if (attr.isEmpty()) {
            iconView.visibility = View.GONE
            textView.visibility = View.GONE
            return
        }
        iconView.visibility = View.VISIBLE
        textView.visibility = View.VISIBLE
        textView.text = attr
        if (iconView.drawable == null) {
            val icon = loadBitmap("attrs/$attr")
            if (icon != null) {
                iconView.setImageBitmap(icon)
            } else {
                iconView.visibility = View.GONE
            }
        }
    }

    private fun categoryColor(category: String): Int = when (category) {
        "常驻异色" -> Color.parseColor("#2e7d32")
        "赛季奇遇" -> Color.parseColor("#e65100")
        else -> Color.parseColor("#9E9E9E")
    }

    private fun loadBitmap(relativePath: String): Bitmap? {
        val cacheKey = relativePath
        imageCache.get(cacheKey)?.let { return it }
        val bitmap = try {
            context.assets.open("img/$relativePath.webp").use { stream ->
                decodeSampledBitmap(stream.readBytes(), 160)
            }
        } catch (e: Exception) {
            null
        }
        if (bitmap != null) {
            imageCache.put(cacheKey, bitmap)
        }
        return bitmap
    }

    private fun loadDexImage(name: String): Bitmap? {
        val cacheKey = "deximg/$name"
        imageCache.get(cacheKey)?.let { return it }
        val bitmap = try {
            context.assets.open("img/dex/$name.webp").use { stream ->
                decodeSampledBitmap(stream.readBytes(), 160)
            }
        } catch (e: Exception) {
            null
        }
        if (bitmap != null) {
            imageCache.put(cacheKey, bitmap)
        }
        return bitmap
    }

    private fun decodeSampledBitmap(bytes: ByteArray, maxSide: Int): Bitmap? {
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

    class PokemonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameView: TextView = itemView.findViewById(R.id.tvName)
        val placeholderView: TextView = itemView.findViewById(R.id.tvPlaceholder)
        val avatarView: ImageView = itemView.findViewById(R.id.ivAvatar)
        val categoryView: TextView = itemView.findViewById(R.id.tvCategory)
        val attrIcon1: ImageView = itemView.findViewById(R.id.ivAttr1)
        val attrText1: TextView = itemView.findViewById(R.id.tvAttr1)
        val attrIcon2: ImageView = itemView.findViewById(R.id.ivAttr2)
        val attrText2: TextView = itemView.findViewById(R.id.tvAttr2)
        val checkView: CheckBox = itemView.findViewById(R.id.cbUnlocked)
    }

    companion object {
        private val maxMemory = (Runtime.getRuntime().maxMemory() / 16).toInt().coerceAtLeast(8 * 1024 * 1024)
    }
}
