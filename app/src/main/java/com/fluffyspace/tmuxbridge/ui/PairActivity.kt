package com.fluffyspace.tmuxbridge.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fluffyspace.tmuxbridge.R
import com.fluffyspace.tmuxbridge.data.ComputerStore
import com.fluffyspace.tmuxbridge.databinding.ActivityPairBinding
import com.fluffyspace.tmuxbridge.model.Computer
import com.fluffyspace.tmuxbridge.net.ApiException
import com.fluffyspace.tmuxbridge.net.PairResult
import com.fluffyspace.tmuxbridge.net.TmuxBridgeClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Pairing flow: take the server IP/port, POST /pair, show the 6-digit code, and
 * poll /pair/<id> until the operator confirms on the server.
 */
class PairActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPairBinding
    private lateinit var store: ComputerStore

    private var pollJob: Job? = null
    private var baseUrl: String = ""
    private var displayName: String = ""
    private var ip: String = ""
    private var port: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPairBinding.inflate(layoutInflater)
        setContentView(binding.root)
        store = ComputerStore(this)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.startButton.setOnClickListener { startPairing() }
        binding.cancelButton.setOnClickListener { cancelPairing() }
    }

    private fun startPairing() {
        ip = binding.ip.text?.toString()?.trim().orEmpty()
        val portText = binding.port.text?.toString()?.trim().orEmpty()
        if (ip.isEmpty()) {
            binding.ip.error = getString(R.string.hint_ip)
            return
        }
        port = portText.toIntOrNull()?.takeIf { it in 1..65535 } ?: run {
            binding.port.error = getString(R.string.hint_port)
            return
        }
        val typedName = binding.name.text?.toString()?.trim().orEmpty()
        displayName = typedName.ifEmpty { ip }
        baseUrl = "http://$ip:$port"

        showPending(null)
        lifecycleScope.launch {
            try {
                val req = TmuxBridgeClient.pair(baseUrl, deviceName())
                showPending(req.pairingCode)
                pollUntilApproved(req.requestId)
            } catch (e: ApiException) {
                fail(e.message ?: getString(R.string.start_pairing))
            }
        }
    }

    private fun deviceName(): String =
        (android.os.Build.MODEL ?: "android").ifBlank { "android" }

    private fun pollUntilApproved(requestId: String) {
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            while (isActive) {
                delay(2_000)
                val result = try {
                    TmuxBridgeClient.pollPair(baseUrl, requestId)
                } catch (e: ApiException) {
                    // Transient network hiccups shouldn't abort; keep polling.
                    if (e.status == 0) continue else { fail(e.message ?: ""); return@launch }
                }
                when (result) {
                    is PairResult.Approved -> {
                        saveAndFinish(result.token)
                        return@launch
                    }
                    PairResult.Denied -> {
                        fail("Pairing was denied on the server")
                        return@launch
                    }
                    PairResult.Pending -> Unit // keep waiting
                }
            }
        }
    }

    private fun saveAndFinish(token: String) {
        store.upsert(
            Computer(
                id = UUID.randomUUID().toString(),
                name = displayName,
                ip = ip,
                port = port,
                token = token,
            ),
        )
        Toast.makeText(this, "Paired with $displayName", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }

    private fun showPending(code: String?) {
        binding.form.visibility = View.GONE
        binding.pendingPanel.visibility = View.VISIBLE
        if (code != null) {
            binding.pairingCode.text = code
            binding.serverCommand.text = "tmux-bridge pair $code"
        }
    }

    private fun cancelPairing() {
        pollJob?.cancel()
        binding.pendingPanel.visibility = View.GONE
        binding.form.visibility = View.VISIBLE
    }

    private fun fail(message: String) {
        pollJob?.cancel()
        if (message.isNotBlank()) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
        binding.pendingPanel.visibility = View.GONE
        binding.form.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        pollJob?.cancel()
        super.onDestroy()
    }
}
