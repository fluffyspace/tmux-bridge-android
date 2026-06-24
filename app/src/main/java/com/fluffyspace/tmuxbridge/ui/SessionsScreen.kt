package com.fluffyspace.tmuxbridge.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import com.fluffyspace.tmuxbridge.R
import com.fluffyspace.tmuxbridge.model.Computer
import com.fluffyspace.tmuxbridge.model.Session
import com.fluffyspace.tmuxbridge.net.ApiException
import com.fluffyspace.tmuxbridge.net.TmuxBridgeClient
import kotlinx.coroutines.launch

/** Second level: the tmux sessions for one paired computer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    computer: Computer,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sessions by remember { mutableStateOf<List<Session>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var showCreate by remember { mutableStateOf(false) }
    var pendingKill by remember { mutableStateOf<Session?>(null) }

    fun toast(msg: String?) =
        Toast.makeText(context, msg ?: "Error", Toast.LENGTH_LONG).show()

    LaunchedEffect(refreshKey) {
        loading = true
        try {
            sessions = TmuxBridgeClient.listSessions(computer)
        } catch (e: ApiException) {
            toast(e.message)
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(computer.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${computer.ip}:${computer.port}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(android.R.string.cancel),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { refreshKey++ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.new_session))
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                loading && sessions.isEmpty() ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                sessions.isEmpty() ->
                    EmptyState(stringResource(R.string.no_sessions))

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 96.dp),
                ) {
                    items(sessions, key = { it.name }) { session ->
                        SessionRow(session = session, onKill = { pendingKill = session })
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateSessionDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, cwd ->
                showCreate = false
                scope.launch {
                    try {
                        val full = TmuxBridgeClient.createSession(computer, name, cwd)
                        toast("Created $full")
                        refreshKey++
                    } catch (e: ApiException) {
                        toast(e.message)
                    }
                }
            },
        )
    }

    pendingKill?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingKill = null },
            title = { Text(stringResource(R.string.kill_session_q, target.name)) },
            text = { Text(stringResource(R.string.kill_session_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingKill = null
                    scope.launch {
                        try {
                            TmuxBridgeClient.killSession(computer, target.name)
                            toast("Killed ${target.name}")
                            refreshKey++
                        } catch (e: ApiException) {
                            toast(e.message)
                        }
                    }
                }) { Text(stringResource(R.string.kill_session)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingKill = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SessionRow(session: Session, onKill: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    session.name,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.titleMedium,
                )
                val state = stringResource(
                    if (session.attached) R.string.attached else R.string.detached,
                )
                val windows = stringResource(R.string.windows_count, session.windows)
                Text(
                    "$state · $windows",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onKill) {
                Text(stringResource(R.string.kill_session))
            }
        }
    }
}

@Composable
private fun CreateSessionDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var cwd by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_session)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { input ->
                        // The daemon only accepts alphanumerics, '-' and '_'.
                        name = input.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
                    },
                    label = { Text(stringResource(R.string.session_name_hint)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = cwd,
                    onValueChange = { cwd = it },
                    label = { Text(stringResource(R.string.cwd_hint)) },
                    supportingText = { Text(stringResource(R.string.cwd_helper)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotEmpty()) onCreate(name, cwd.trim().ifBlank { null }) },
                enabled = name.isNotEmpty(),
            ) { Text(stringResource(R.string.create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
