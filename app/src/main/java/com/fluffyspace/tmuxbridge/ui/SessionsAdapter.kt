package com.fluffyspace.tmuxbridge.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.fluffyspace.tmuxbridge.R
import com.fluffyspace.tmuxbridge.databinding.ItemSessionBinding
import com.fluffyspace.tmuxbridge.model.Session

class SessionsAdapter(
    private val onKill: (Session) -> Unit,
) : RecyclerView.Adapter<SessionsAdapter.VH>() {

    private val items = mutableListOf<Session>()

    fun submit(list: List<Session>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(val binding: ItemSessionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemSessionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false,
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val session = items[position]
        val ctx = holder.binding.root.context
        holder.binding.name.text = session.name
        val state = ctx.getString(
            if (session.attached) R.string.attached else R.string.detached,
        )
        val windows = ctx.getString(R.string.windows_count, session.windows)
        holder.binding.detail.text = "$state · $windows"
        holder.binding.kill.setOnClickListener { onKill(session) }
    }

    override fun getItemCount() = items.size
}
