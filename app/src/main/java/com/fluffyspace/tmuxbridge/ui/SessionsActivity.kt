package com.fluffyspace.tmuxbridge.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fluffyspace.tmuxbridge.R
import com.fluffyspace.tmuxbridge.data.ComputerStore
import com.fluffyspace.tmuxbridge.databinding.ActivitySessionsBinding
import com.fluffyspace.tmuxbridge.databinding.DialogCreateSessionBinding
import com.fluffyspace.tmuxbridge.model.Computer
import com.fluffyspace.tmuxbridge.model.Session
import com.fluffyspace.tmuxbridge.net.ApiException
import com.fluffyspace.tmuxbridge.net.TmuxBridgeClient
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** Second level: the tmux sessions for one paired computer. */
class SessionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySessionsBinding
    private lateinit var adapter: SessionsAdapter
    private lateinit var computer: Computer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySessionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val id = intent.getStringExtra(EXTRA_COMPUTER_ID)
        val loaded = id?.let { ComputerStore(this).get(it) }
        if (loaded == null) {
            finish()
            return
        }
        computer = loaded

        binding.toolbar.title = computer.name
        binding.toolbar.subtitle = "${computer.ip}:${computer.port}"
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.inflateMenu(R.menu.menu_sessions)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_refresh) {
                loadSessions(); true
            } else false
        }

        adapter = SessionsAdapter(onKill = ::confirmKill)
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.fab.setOnClickListener { showCreateDialog() }

        loadSessions()
    }

    private fun loadSessions() {
        binding.loading.visibility = View.VISIBLE
        binding.empty.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val sessions = TmuxBridgeClient.listSessions(computer)
                showSessions(sessions)
            } catch (e: ApiException) {
                binding.loading.visibility = View.GONE
                toast(e.message)
            }
        }
    }

    private fun showSessions(sessions: List<Session>) {
        binding.loading.visibility = View.GONE
        adapter.submit(sessions)
        binding.empty.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showCreateDialog() {
        val dialogBinding = DialogCreateSessionBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.create_session)
            .setView(dialogBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = dialogBinding.sessionName.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) createSession(name)
            }
            .show()
    }

    private fun createSession(name: String) {
        binding.loading.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val fullName = TmuxBridgeClient.createSession(computer, name)
                toast("Created $fullName")
                loadSessions()
            } catch (e: ApiException) {
                binding.loading.visibility = View.GONE
                toast(e.message)
            }
        }
    }

    private fun confirmKill(session: Session) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.kill_session_q, session.name))
            .setMessage(R.string.kill_session_msg)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.kill_session) { _, _ -> killSession(session) }
            .show()
    }

    private fun killSession(session: Session) {
        binding.loading.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                TmuxBridgeClient.killSession(computer, session.name)
                toast("Killed ${session.name}")
                loadSessions()
            } catch (e: ApiException) {
                binding.loading.visibility = View.GONE
                toast(e.message)
            }
        }
    }

    private fun toast(message: String?) {
        Toast.makeText(this, message ?: "Error", Toast.LENGTH_LONG).show()
    }

    companion object {
        const val EXTRA_COMPUTER_ID = "computer_id"
    }
}
