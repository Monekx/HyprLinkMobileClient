package com.monekx.hyprlink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.monekx.hyprlink.ui.theme.HyprLinkTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HyprLinkTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val config = viewModel.uiConfig
                    if (viewModel.isConnected && config != null) {
                        DynamicScreen(
                            config = config,
                            moduleValues = viewModel.moduleValues,
                            moduleTexts = viewModel.moduleTexts,
                            onAction = { id, value -> viewModel.sendAction(id, value) }
                        )
                    } else {
                        LoginScreen(
                            isConnecting = viewModel.isConnecting,
                            error = viewModel.error,
                            servers = viewModel.discoveredServers,
                            // Убрали context из параметров
                            onConnect = { ip, pin ->
                                viewModel.connect(ip, 8080, pin)
                            }
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun LoginScreen(
        isConnecting: Boolean,
        error: String?,
        servers: List<DiscoveredServer>,
        onConnect: (String, String) -> Unit // Убрали Context
    ) {
        var ip by remember { mutableStateOf("") }
        var pinValue by remember { mutableStateOf("") }
        var showPinDialog by remember { mutableStateOf(false) }

        LaunchedEffect(error) {
            if (error == "unauthorized") {
                showPinDialog = true
            }
        }

        if (showPinDialog) {
            AlertDialog(
                onDismissRequest = {
                    showPinDialog = false
                    viewModel.clearError()
                },
                title = { Text("Требуется PIN-код") },
                text = {
                    Column {
                        Text("Введите код, отображенный на вашем ПК")
                        TextField(
                            value = pinValue,
                            onValueChange = { pinValue = it },
                            modifier = Modifier.padding(top = 8.dp),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val pinToSend = pinValue
                            showPinDialog = false
                            onConnect(ip, pinToSend)
                            pinValue = ""
                        },
                        enabled = pinValue.isNotEmpty()
                    ) { Text("Войти") }
                }
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("HyprLink", style = MaterialTheme.typography.headlineLarge)

            if (servers.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Обнаруженные серверы:", style = MaterialTheme.typography.labelMedium)
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(servers) { server ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            onClick = { ip = server.ip }
                        ) {
                            ListItem(
                                headlineContent = { Text(server.hostname) },
                                supportingContent = { Text(server.ip) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            TextField(
                value = ip,
                onValueChange = { ip = it },
                label = { Text("IP Address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    viewModel.clearError()
                    onConnect(ip, "")
                },
                enabled = !isConnecting && !showPinDialog,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text("Connect")
                }
            }

            if (error != null && error != "unauthorized") {
                Text(
                    text = "Ошибка: $error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}