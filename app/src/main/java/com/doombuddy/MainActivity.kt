package com.doombuddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.doombuddy.data.PrefsRepository
import com.doombuddy.data.StatsRepository
import com.doombuddy.monitor.MonitorService
import com.doombuddy.ui.debug.DebugScreen
import com.doombuddy.ui.home.HomeScreen
import com.doombuddy.ui.onboarding.OnboardingScreen
import com.doombuddy.ui.settings.SettingsScreen
import androidx.lifecycle.lifecycleScope
import com.doombuddy.ui.theme.DoomBuddyTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var prefs: PrefsRepository
    private lateinit var stats: StatsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = PrefsRepository(this)
        stats = StatsRepository(this)

        setContent {
            DoomBuddyTheme {
                val nav = rememberNavController()
                val prefsState by prefs.preferences.collectAsState(initial = null)
                val startDestination = remember(prefsState?.onboardingDone) {
                    if (prefsState?.onboardingDone == true) "home" else "onboarding"
                }
                val showOnboardingOnce = remember { mutableStateOf(true) }

                NavHost(navController = nav, startDestination = startDestination) {
                    composable("onboarding") {
                        OnboardingScreen(prefs) {
                            showOnboardingOnce.value = false
                            nav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
                        }
                    }
                    composable("home") {
                        HomeScreen(
                            prefs = prefs,
                            stats = stats,
                            onOpenSettings = { nav.navigate("settings") },
                            onOpenDebug = { nav.navigate("debug") },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(prefs) { nav.popBackStack() }
                    }
                    composable("debug") {
                        DebugScreen(prefs) { nav.popBackStack() }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Self-heal: if onboarding is done and the service died (process death, force stop),
        // bring it back whenever the user opens the app.
        lifecycleScope.launch {
            val done = prefs.preferences.first().onboardingDone
            android.util.Log.d("DoomBuddy", "onResume self-heal: onboardingDone=$done, serviceRunning=${MonitorService.instance != null}")
            if (done) MonitorService.start(this@MainActivity)
        }
    }
}
