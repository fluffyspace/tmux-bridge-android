package com.fluffyspace.tmuxbridge.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fluffyspace.tmuxbridge.data.ComputerStore
import com.fluffyspace.tmuxbridge.databinding.ActivityComputersBinding
import com.fluffyspace.tmuxbridge.model.Computer
import com.fluffyspace.tmuxbridge.net.TmuxBridgeClient
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** Top level: the list of paired computers. */
class ComputersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityComputersBinding
    private lateinit var store: ComputerStore
    private lateinit var adapter: ComputersAdapter

    private val pairLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityComputersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = ComputerStore(this)
        adapter = ComputersAdapter(onOpen = ::openSessions, onRemove = ::confirmRemove)
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.fab.setOnClickListener {
            pairLauncher.launch(Intent(this, PairActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val computers = store.all()
        adapter.submit(computers)
        binding.empty.visibility = if (computers.isEmpty()) android.view.View.VISIBLE
        else android.view.View.GONE
    }

    private fun openSessions(computer: Computer) {
        startActivity(
            Intent(this, SessionsActivity::class.java)
                .putExtra(SessionsActivity.EXTRA_COMPUTER_ID, computer.id),
        )
    }

    private fun confirmRemove(computer: Computer) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(com.fluffyspace.tmuxbridge.R.string.remove_computer_q, computer.name))
            .setMessage(com.fluffyspace.tmuxbridge.R.string.remove_computer_msg)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(com.fluffyspace.tmuxbridge.R.string.remove_computer) { _, _ ->
                removeComputer(computer)
            }
            .show()
    }

    private fun removeComputer(computer: Computer) {
        // Best-effort revoke on the daemon, then forget locally regardless.
        lifecycleScope.launch {
            runCatching { TmuxBridgeClient.unpairSelf(computer) }
            store.remove(computer.id)
            refresh()
        }
    }
}
