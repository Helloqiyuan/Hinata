package com.qiyuan.hinata.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.qiyuan.hinata.MainViewModel
import com.qiyuan.hinata.databinding.ItemDataRowBinding

/**
 * 「数据」Tab 的键值列表适配器（主数值大号加粗）。
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

    class VH(private val binding: ItemDataRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: MainViewModel.DataRow) {
            binding.textTitle.text = row.title
            binding.textValue.text = row.value
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
        private val DIFF = object : DiffUtil.ItemCallback<MainViewModel.DataRow>() {
            override fun areItemsTheSame(
                oldItem: MainViewModel.DataRow,
                newItem: MainViewModel.DataRow,
            ): Boolean = oldItem.title == newItem.title

            override fun areContentsTheSame(
                oldItem: MainViewModel.DataRow,
                newItem: MainViewModel.DataRow,
            ): Boolean = oldItem == newItem
        }
    }
}
