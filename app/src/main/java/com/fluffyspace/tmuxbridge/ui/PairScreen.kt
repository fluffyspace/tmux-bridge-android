package com.fluffyspace.tmuxbridge.ui

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluffyspace.tmuxbridge.R
import com.fluffyspace.tmuxbridge.data.ComputerStore
import com.fluffyspace.tmuxbridge.model.Computer
import com.fluffyspace.tmuxbridge.net.ApiException
import com.fluffyspace.tmuxbridge.net.PairResult
import com.fluffyspace.tmuxbridge.net.TmuxBridgeClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Pairing flow: collect IP/port, POST /pair, show the 6-digit code, and poll
 * /pair/<id> until the operator confirms with `tmux-bridge pair <code>`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairScreen(
    store: ComputerStore,
    onBack: () -> Unit,
    onPaired: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("7842") }
    var name by remember { mutableStateOf("") }
    var connecting by remember { mutableStateOf(false) }

    // Pairing-in-flight state. requestId != null means we're polling.
    var requestId by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf<String?>(null) }
    var pairedIp by remember { mutableStateOf("") }
    var pairedPort by remember { mutableStateOf(0) }
    var pairedName by remember { mutableStateOf("") }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    fun reset() {
        requestId = null
        code = null
    }

    fun start() {
        val ipTrim = ip.trim()
        val portNum = port.trim().toIntOrNull()?.takeIf { it in 1..65535 }
        if (ipTrim.isEmpty()) {
            toast(context.getString(R.string.hint_ip)); return
        }
        if (portNum == null) {
            toast(context.getString(R.string.hint_port)); return
        }
        pairedIp = ipTrim
        pairedPort = portNum
        pairedName = name.trim().ifEmpty { ipTrim }
        connecting = true
        scope.launch {
            try {
                val req = TmuxBridgeClient.pair(
                    baseUrl = "http://$ipTrim:$portNum",
                    deviceName = (Build.MODEL ?: "android").ifBlank { "android" },
                )
                code = req.pairingCode
                requestId = req.requestId
            } catch (e: ApiException) {
                toast(e.message ?: "Pairing failed")
            } finally {
                connecting = false
            }
        }
    }

    // Poll while a request is in flight; auto-cancels when requestId clears or
    // the screen leaves composition.
    LaunchedEffect(requestId) {
        val id = requestId ?: return@LaunchedEffect
        val baseUrl = "http://$pairedIp:$pairedPort"
        while (true) {
            delay(2_000)
            val result = try {
                TmuxBridgeClient.pollPair(baseUrl, id)
            } catch (e: ApiException) {
                if (e.status == 0) continue // transient network hiccup; keep waiting
                toast(e.message ?: "Pairing failed"); reset(); return@LaunchedEffect
            }
            when (result) {
                is PairResult.Approved -> {
                    store.upsert(
                        Computer(
                            id = UUID.randomUUID().toString(),
                            name = pairedName,
                            ip = pairedIp,
                            port = pairedPort,
                            token = result.token,
                        ),
                    )
                    toast("Paired with $pairedName")
                    onPaired()
                    return@LaunchedEffect
                }
                PairResult.Denied -> {
                    toast("Pairing was denied on the server"); reset(); return@LaunchedEffect
                }
                PairResult.Pending -> Unit // keep waiting
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pair_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(android.R.string.cancel),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            if (requestId == null) {
                PairForm(
                    ip = ip, onIp = { ip = it },
                    port = port, onPort = { port = it },
                    name = name, onName = { name = it },
                    connecting = connecting,
                    onStart = ::start,
                )
            } else {
                PendingPanel(code = code.orEmpty(), onCancel = ::reset)
            }
        }
    }
}

@Composable
private fun PairForm(
    ip: String, onIp: (String) -> Unit,
    port: String, onPort: (String) -> Unit,
    name: String, onName: (String) -> Unit,
    connecting: Boolean,
    onStart: () -> Unit,
) {
    OutlinedTextField(
        value = ip,
        onValueChange = { onIp(it.filter { c -> c.isDigit() || c == '.' }) },
        label = { Text(stringResource(R.string.hint_ip)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = port,
        onValueChange = { onPort(it.filter(Char::isDigit)) },
        label = { Text(stringResource(R.string.hint_port)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    )
    OutlinedTextField(
        value = name,
        onValueChange = onName,
        label = { Text(stringResource(R.string.hint_name)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    )
    Button(
        onClick = onStart,
        enabled = !connecting,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
    ) {
        if (connecting) {
            CircularProgressIndicator(
                modifier = Modifier.padding(end = 8.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Text(stringResource(R.string.start_pairing))
    }
}

@Composable
private fun PendingPanel(code: String, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.pairing_instructions),
            style = MaterialTheme.typography.bodyMedium,
        )
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text(
                "tmux-bridge pair $code",
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(14.dp),
            )
        }
        Text(
            stringResource(R.string.pairing_code_label).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = code,
            fontFamily = FontFamily.Monospace,
            fontSize = 56.sp,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
        Text(
            stringResource(R.string.waiting_for_confirmation),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        TextButton(onClick = onCancel, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.cancel))
        }
    }
}
