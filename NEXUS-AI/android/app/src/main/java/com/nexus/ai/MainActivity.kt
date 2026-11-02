package com.nexus.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.nexus.ai.data.NexusViewModel
import com.nexus.ai.navigation.NexusAppShell
import com.nexus.ai.theme.NexusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vmResult = runCatching {
            ViewModelProvider(this)[NexusViewModel::class.java]
        }
        val vmInstance = vmResult.getOrNull()
        val initError = vmResult.exceptionOrNull()

        setContent {
            NexusTheme(darkTheme = vmInstance?.darkThemeEnabled ?: true) {
                if (vmInstance != null) {
                    NexusAppShell(vm = vmInstance)
                } else {
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
                            Text("NEXUS AI — Pemulihan Startup", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Kendala inisialisasi: ${initError?.javaClass?.simpleName}: ${initError?.message}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { recreate() }) {
                                Text("Coba Muat Ulang")
                            }
                        }
                    }
                }
            }
        }
    }
}
