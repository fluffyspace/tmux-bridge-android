package com.fluffyspace.tmuxbridge.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fluffyspace.tmuxbridge.R
import com.fluffyspace.tmuxbridge.data.ComputerStore
import com.fluffyspace.tmuxbridge.model.Computer
import com.fluffyspace.tmuxbridge.net.TmuxBridgeClient
import kotlinx.coroutines.launch

/** Top level: the list of paired computers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComputersScreen(
    store: ComputerStore,
    onAddComputer: () -> Unit,
    onOpenComputer: (Computer) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var computers by remember { mutableStateOf(store.all()) }
    var pendingRemoval by remember { mutableStateOf<Computer?>(null) }

    // Refresh whenever this screen (re)enters composition, e.g. after pairing.
    LaunchedEffect(Unit) { computers = store.all() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.computers_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddComputer) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_computer))
            }
        },
    ) { padding ->
        if (computers.isEmpty()) {
            EmptyState(stringResource(R.string.no_computers), Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 6.dp, bottom = 96.dp,
                ),
            ) {
                items(computers, key = { it.id }) { computer ->
                    ComputerRow(
                        computer = computer,
                        onOpen = { onOpenComputer(computer) },
                        onRemove = { pendingRemoval = computer },
                    )
                }
            }
        }
    }

    pendingRemoval?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(stringResource(R.string.remove_computer_q, target.name)) },
            text = { Text(stringResource(R.string.remove_computer_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingRemoval = null
                    scope.launch {
                        // Best-effort token revoke, then forget locally regardless.
                        runCatching { TmuxBridgeClient.unpairSelf(target) }
                        store.remove(target.id)
                        computers = store.all()
                    }
                }) { Text(stringResource(R.string.remove_computer)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ComputerRow(
    computer: Computer,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(computer.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${computer.ip}:${computer.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.remove_computer),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
internal fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
