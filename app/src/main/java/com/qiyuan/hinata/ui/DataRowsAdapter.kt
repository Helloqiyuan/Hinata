package com.qiyuan.hinata.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.qiyuan.hinata.MainViewModel
import com.qiyuan.hinata.R
import com.qiyuan.hinata.databinding.ItemDataRowBinding

/**
 * 「数据」Tab 的键值列表适配器（主数值大号加粗 + 轮换彩色卡片底）。
 */
class DataRowsAdapter : ListAdapter<MainViewModel.DataRow, DataRowsAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemDataRowBinding.inflate(inflater, parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            holder.bind(getItem(position))
        }
    }

    class VH(private val binding: ItemDataRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: MainViewModel.DataRow) {
            val ctx = binding.root.context
            val card = binding.root as MaterialCardView
            val idx = row.cardTintIndex.mod(CARD_TINT_RES.size)
            card.setCardBackgroundColor(ContextCompat.getColor(ctx, CARD_TINT_RES[idx]))

            binding.textTitle.text = row.title
            binding.textValue.text = row.value
            if (row.valueAccentColorRes != null) {
                binding.textValue.setTextColor(ContextCompat.getColor(ctx, row.valueAccentColorRes))
            } else {
                binding.textValue.setTextColor(
                    MaterialColors.getColor(
                        ctx,
                        com.google.android.material.R.attr.colorOnSurface,
                        Color.BLACK,
                    ),
                )
            }
            val foot = row.footer
            if (foot.isNullOrBlank()) {
                binding.textFooter.visibility = View.GONE
            } else {
                binding.textFooter.visibility = View.VISIBLE
                binding.textFooter.text = foot
            }
        }
    }

    companion object {
        private val CARD_TINT_RES = intArrayOf(
            R.color.hinata_card_tint_0,
            R.color.hinata_card_tint_1,
            R.color.hinata_card_tint_2,
            R.color.hinata_card_tint_3,
            R.color.hinata_card_tint_4,
            R.color.hinata_card_tint_5,
        )

        private val DIFF = object : DiffUtil.ItemCallback<MainViewModel.DataRow>() {
            override fun areItemsTheSame(
                oldItem: MainViewModel.DataRow,
                newItem: MainViewModel.DataRow,
            ): Boolean = oldItem.title == newItem.title

            override fun areContentsTheSame(
                oldItem: MainViewModel.DataRow,
                newItem: MainViewModel.DataRow,
            ): Boolean = oldItem == newItem

            override fun getChangePayload(
                oldItem: MainViewModel.DataRow,
                newItem: MainViewModel.DataRow,
            ): Any? = if (oldItem.title == newItem.title && oldItem != newItem) PAYLOAD_ROW else null
        }

        private val PAYLOAD_ROW = Any()
    }
}
