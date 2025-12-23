package com.monekx.hyprlink

import android.app.Application
import android.content.*
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val discoveryManager = DiscoveryManager()
    private var isBound = false

    var uiConfig by mutableStateOf<UIConfig?>(null)
    var isConnected by mutableStateOf(false)
    var isConnecting by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    val discoveredServers = mutableStateListOf<DiscoveredServer>()
    val moduleValues = mutableStateMapOf<String, Float>()
    val moduleTexts = mutableStateMapOf<String, String>()

    // Используем WeakReference, чтобы линтер не ругался на утечку контекста
    private var serviceRef: WeakReference<HyprLinkService>? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as HyprLinkService.LocalBinder
            val instance = binder.getService()
            serviceRef = WeakReference(instance)
            isBound = true
            observeService(instance)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceRef = null
            isBound = false
        }
    }

    init {
        viewModelScope.launch {
            // Вещаем и ждем ответа от Arch (ACK|port)
            discoveryManager.startBroadcasting(Build.MODEL, 8080) { server ->
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
        // Достаем сервис из слабой ссылки
        serviceRef?.get()?.sendAction(id, value)
    }

    private fun unbindServiceIfBound() {
        if (isBound) {
            try {
                getApplication<Application>().unbindService(serviceConnection)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isBound = false
            serviceRef = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        unbindServiceIfBound()
    }
}