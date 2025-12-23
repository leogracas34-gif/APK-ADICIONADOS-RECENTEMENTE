package com.vltv.play

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.vltv.play.databinding.ItemRecentBinding

class RecentItemAdapterFast(
    private val onClick: (RecentItem) -> Unit
) : ListAdapter<RecentItem, RecentItemAdapterFast.VH>(RecentItemDiffCallback()) {

    inner class VH(val binding: ItemRecentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: RecentItem) {
            binding.tvTitleRecent.text = item.title
            Glide.with(binding.root.context)
                .load(item.icon)
                .placeholder(android.R.color.darker_gray)
                .centerCrop()
                .into(binding.imgPosterRecent)
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val binding = ItemRecentBinding.inflate(
            android.view.LayoutInflater.from(parent.context),
            parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class RecentItemDiffCallback : DiffUtil.ItemCallback<RecentItem>() {
        override fun areItemsTheSame(old: RecentItem, new: RecentItem) = old.streamId == new.streamId
        override fun areContentsTheSame(old: RecentItem, new: RecentItem) = old == new
    }
}
