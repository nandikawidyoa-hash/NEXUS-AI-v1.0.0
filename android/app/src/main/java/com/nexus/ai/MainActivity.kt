package com.nexus.ai

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexus.ai.data.NexusViewModel
import com.nexus.ai.navigation.NexusAppShell
import com.nexus.ai.theme.NexusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var startupError by remember { mutableStateOf<String?>(null) }

            if (startupError != null) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("NEXUS AI — Pemulihan Startup", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Aplikasi mendeteksi kendala inisialisasi:\n$startupError",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            startupError = null
                            recreate()
                        }) {
                            Text("Coba Muat Ulang")
                        }
                    }
                }
            } else {
                try {
                    val vm: NexusViewModel = viewModel()
                    NexusTheme(darkTheme = vm.darkThemeEnabled) {
                        NexusAppShell(vm = vm)
                    }
                } catch (t: Throwable) {
                    Log.e("NEXUS_STARTUP", "Error during Compose setup", t)
                    startupError = "${t.javaClass.simpleName}: ${t.message}\n\n${t.stackTraceToString().take(600)}"
                }
            }
        }
    }
}
