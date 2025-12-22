package com.monekx.hyprlink

import android.app.Application
import android.content.*
import android.os.IBinder
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// Наследуемся от AndroidViewModel, чтобы получить доступ к Application Context
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val discoveryManager = DiscoveryManager()

    // Поле boundContext больше не нужно, утечки нет
    private var isBound = false

    var uiConfig by mutableStateOf<UIConfig?>(null)
    var isConnected by mutableStateOf(false)
    var isConnecting by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    val discoveredServers = mutableStateListOf<DiscoveredServer>()
    val moduleValues = mutableStateMapOf<String, Float>()
    val moduleTexts = mutableStateMapOf<String, String>()

    private var hyprService: HyprLinkService? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as HyprLinkService.LocalBinder
            val instance = binder.getService()
            hyprService = instance
            isBound = true
            observeService(instance)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            hyprService = null
            isBound = false
        }
    }

    init {
        viewModelScope.launch {
            discoveryManager.startDiscovery { server ->
                if (discoveredServers.none { it.ip == server.ip }) {
                    discoveredServers.add(server)
                }
            }
        }
    }

    fun clearError() {
        error = null
    }

    private fun observeService(service: HyprLinkService) {
        viewModelScope.launch {
            service.configFlow.collectLatest { config ->
                if (config != null) {
                    moduleValues.clear()
                    moduleTexts.clear()
                    uiConfig = config
                }
            }
        }
        viewModelScope.launch {
            service.updatesFlow
                .buffer(Channel.CONFLATED)
                .collect { update ->
                    update.id?.let { id ->
                        if (update.content != null) {
                            moduleTexts[id] = update.content
                        } else if (update.value != null) {
                            moduleValues[id] = update.value.toFloat()
                        }
                    }
                }
        }
        viewModelScope.launch {
            service.connectionState.collectLatest { state ->
                isConnected = state == ConnectionState.CONNECTED
                isConnecting = state == ConnectionState.CONNECTING
            }
        }
        viewModelScope.launch {
            service.errorFlow.collectLatest { msg -> error = msg }
        }
    }

    // Убрали параметр context, берем его из getApplication()
    fun connect(ip: String, port: Int, pin: String) {
        unbindServiceIfBound()

        error = null
        isConnecting = true

        val context = getApplication<Application>()
        val intent = Intent(context, HyprLinkService::class.java).apply {
            putExtra("ip", ip)
            putExtra("port", port)
            putExtra("pin", pin)
        }

        context.startForegroundService(intent)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun sendAction(id: String, value: Double? = null) {
        hyprService?.sendAction(id, value)
    }

    private fun unbindServiceIfBound() {
        if (isBound) {
            try {
                getApplication<Application>().unbindService(serviceConnection)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isBound = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        unbindServiceIfBound()
        hyprService = null
    }
}