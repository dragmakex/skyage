package com.skyage.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skyage.app.ui.AddFlightScreen
import com.skyage.app.ui.FlightDetailScreen
import com.skyage.app.ui.HomeScreen
import com.skyage.app.ui.VoyageScreen
import com.skyage.app.ui.theme.SkyageTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SkyageTheme {
                val vm: AppViewModel = viewModel()
                SkyageRoot(vm)
            }
        }
    }
}

private sealed interface Screen {
    data object Home : Screen
    data object Add : Screen
    data class Detail(val key: String) : Screen
    data object Voyage : Screen
}

/** Rotation rebuilds the activity; the screen the user was on has to survive that. */
private val ScreenSaver = listSaver<Screen, String>(
    save = {
        when (it) {
            Screen.Home -> listOf("home")
            Screen.Add -> listOf("add")
            Screen.Voyage -> listOf("voyage")
            is Screen.Detail -> listOf("detail", it.key)
        }
    },
    restore = {
        when (it.firstOrNull()) {
            "add" -> Screen.Add
            "voyage" -> Screen.Voyage
            "detail" -> Screen.Detail(it.getOrElse(1) { "" })
            else -> Screen.Home
        }
    },
)

@Composable
private fun SkyageRoot(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var screen by rememberSaveable(stateSaver = ScreenSaver) { mutableStateOf<Screen>(Screen.Home) }

    // Keep the live snapshot fresh while the UI is on screen.
    LaunchedEffect(state.ready) {
        if (!state.ready) return@LaunchedEffect
        while (true) {
            delay(vm.pollIntervalMs())
            vm.refresh(force = true)
        }
    }

    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    when (val current = screen) {
        Screen.Home -> HomeScreen(
            state = state,
            airports = vm.allAirports(),
            onAdd = { screen = Screen.Add },
            onOpen = { key -> screen = Screen.Detail(key) },
            onVoyage = { screen = Screen.Voyage },
            onRefresh = { vm.refresh(force = true) },
        )

        Screen.Voyage -> VoyageScreen(
            trips = state.trips,
            airports = vm.allAirports(),
            onBack = { screen = Screen.Home },
        )

        Screen.Add -> AddFlightScreen(
            state = state,
            onBack = { screen = Screen.Home },
            onSearchAirports = { vm.searchAirports(it) },
            onResolve = { vm.resolve(it) },
            onSave = { parsed, origin, dest ->
                val ok = vm.addFlight(parsed, origin, dest)
                if (ok) vm.refresh(force = true)
                ok
            },
        )

        is Screen.Detail -> {
            val view = vm.viewFor(current.key)
            if (view == null) {
                screen = Screen.Home
            } else {
                FlightDetailScreen(
                    view = view,
                    airports = vm.allAirports(),
                    onBack = { screen = Screen.Home },
                    onRefresh = { vm.refresh(force = true) },
                    onDelete = {
                        vm.removeFlight(current.key)
                        screen = Screen.Home
                    },
                )
            }
        }
    }
}
