package com.volttrack.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.volttrack.app.data.ChargingSessionRecorder
import com.volttrack.app.ui.VoltTrackAppContent
import com.volttrack.app.ui.main.MainViewModel
import com.volttrack.app.ui.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onStart() {
        super.onStart()
        // Force a full reconciliation
        lifecycleScope.launch(Dispatchers.IO) {
            ChargingSessionRecorder.recoverOrphanedSessionIfUnplugged(this@MainActivity)
        }
        mainViewModel.onForegroundChargingCheck()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VoltTrackAppContent(
                mainViewModel = mainViewModel,
                settingsViewModel = settingsViewModel
            )
        }
    }
}
