package com.fluffyspace.tmuxbridge.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.fluffyspace.tmuxbridge.databinding.ItemComputerBinding
import com.fluffyspace.tmuxbridge.model.Computer

class ComputersAdapter(
    private val onOpen: (Computer) -> Unit,
    private val onRemove: (Computer) -> Unit,
) : RecyclerView.Adapter<ComputersAdapter.VH>() {

    private val items = mutableListOf<Computer>()

    fun submit(list: List<Computer>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(val binding: ItemComputerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemComputerBinding.inflate(
            LayoutInflater.from(parent.context), parent, false,
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val computer = items[position]
        holder.binding.name.text = computer.name
        holder.binding.address.text = "${computer.ip}:${computer.port}"
        holder.binding.root.setOnClickListener { onOpen(computer) }
        holder.binding.remove.setOnClickListener { onRemove(computer) }
    }

    override fun getItemCount() = items.size
}
