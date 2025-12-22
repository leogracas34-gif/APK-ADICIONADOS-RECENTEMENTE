package com.vltv.play

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.vltv.play.databinding.ItemRecentBinding

class RecentesAdapter(
    private val itens: List<ConteudoRecente>,
    private val onClick: (ConteudoRecente) -> Unit
) : RecyclerView.Adapter<RecentesAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemRecentBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ConteudoRecente) {
            binding.tvTitleRecent.text = item.titulo

            Glide.with(binding.root.context)
                .load(item.capa)
                .placeholder(android.R.color.darker_gray)
                .into(binding.imgPosterRecent)

            binding.root.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(itens[position])
    }

    override fun getItemCount(): Int = itens.size
}
