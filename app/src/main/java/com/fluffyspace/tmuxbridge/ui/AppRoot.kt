package com.fluffyspace.tmuxbridge.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.platform.LocalContext
import com.fluffyspace.tmuxbridge.data.ComputerStore

/** The two-level navigation destinations. */
sealed interface Screen {
    data object Computers : Screen
    data object Pair : Screen
    data class Sessions(val computerId: String) : Screen
}

/**
 * Holds the back stack and dispatches to each screen. A plain
 * [SnapshotStateList] back stack keeps the app dependency-light (no
 * navigation-compose) while still supporting the system back button.
 */
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val store = remember { ComputerStore(context) }
    val backStack = remember { mutableListOf<Screen>(Screen.Computers).toMutableStateList() }
    val current = backStack.last()

    fun navigate(screen: Screen) = backStack.add(screen)
    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    BackHandler(enabled = backStack.size > 1) { pop() }

    when (current) {
        Screen.Computers -> ComputersScreen(
            store = store,
            onAddComputer = { navigate(Screen.Pair) },
            onOpenComputer = { navigate(Screen.Sessions(it.id)) },
        )

        Screen.Pair -> PairScreen(
            store = store,
            onBack = { pop() },
            onPaired = { pop() },
        )

        is Screen.Sessions -> {
            val computer = remember(current) { store.get(current.computerId) }
            // If the computer vanished (e.g. removed), fall back to the list.
            LaunchedEffect(computer) { if (computer == null) pop() }
            if (computer != null) {
                SessionsScreen(computer = computer, onBack = { pop() })
            }
        }
    }
}
