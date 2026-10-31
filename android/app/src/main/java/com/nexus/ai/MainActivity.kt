package com.nexus.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexus.ai.data.NexusViewModel
import com.nexus.ai.navigation.NexusAppShell
import com.nexus.ai.theme.NexusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: NexusViewModel = viewModel()
            NexusTheme(darkTheme = vm.darkThemeEnabled) {
                NexusAppShell(vm = vm)
            }
        }
    }
}
