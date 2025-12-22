package com.monekx.hyprlink

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.BufferedWriter
import java.net.InetSocketAddress
import java.net.Socket

class HyprLinkService : Service() {
    private val binder = LocalBinder()
    private val gson = Gson()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var mediaSession: MediaSessionCompat? = null

    private var lastIp: String? = null
    private var lastPort: Int = 8080
    private var isRetryJobActive = false

    val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.DISCONNECTED)
    val configFlow = MutableStateFlow<UIConfig?>(null)
    val updatesFlow = MutableSharedFlow<ServerResponse>(extraBufferCapacity = 64)
    val errorFlow = MutableSharedFlow<String>(extraBufferCapacity = 16)

    inner class LocalBinder : Binder() {
        fun getService(): HyprLinkService = this@HyprLinkService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        setupMediaSession()
        createNotificationChannel()
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "HyprLinkMedia").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { sendAction("media_play", null) }
                override fun onPause() { sendAction("media_pause", null) }
                override fun onSkipToNext() { sendAction("media_next", null) }
                override fun onSkipToPrevious() { sendAction("media_prev", null) }
                override fun onSeekTo(pos: Long) {
                    // Отправляем позицию в секундах, так как playerctl ждет их
                    sendAction("media_seek", pos.toDouble() / 1000.0)
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "SEND_NOTIFICATION") {
            val title = intent.getStringExtra("title")
            val text = intent.getStringExtra("text")
            val app = intent.getStringExtra("app")
            sendNotificationToServer(title, text, app)
            return START_STICKY
        }

        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(1, notification)
        }
        val ip = intent?.getStringExtra("ip")
        val port = intent?.getIntExtra("port", 8080) ?: 8080
        val pin = intent?.getStringExtra("pin") ?: ""

        if (ip != null) {
            lastIp = ip
            lastPort = port
            connectToServer(ip, port, pin)
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "hyprlink_service",
                "HyprLink Connection",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            // FLAG_ACTIVITY_SINGLE_TOP предотвращает создание новой активити, если она уже открыта
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val style = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(mediaSession?.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        return NotificationCompat.Builder(this, "hyprlink_service")
            .setContentTitle("HyprLink")
            .setContentText("Сервис активен")
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setContentIntent(pendingIntent) // Это обработает нажатие на само уведомление
            .setStyle(style)
            .setOngoing(true)
            .build()
    }

    private fun connectToServer(ip: String, port: Int, pin: String) {
        val prefs = getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
        val savedId = prefs.getString("device_id", "")
        val savedToken = prefs.getString("token", "")

        serviceScope.launch {
            try {
                val currentSocket = socket
                if (currentSocket != null && !currentSocket.isClosed && pin.isNotEmpty()) {
                    sendPin(pin)
                    return@launch
                }

                connectionState.value = ConnectionState.CONNECTING
                val s = Socket()
                s.connect(InetSocketAddress(ip, port), 5000)
                socket = s

                val w = s.getOutputStream().bufferedWriter()
                val r = s.getInputStream().bufferedReader()
                writer = w

                val authReq = mutableMapOf<String, Any>(
                    "hash" to (configFlow.value?.hash ?: "")
                )

                if (!savedToken.isNullOrEmpty()) {
                    authReq["device_id"] = savedId ?: ""
                    authReq["token"] = savedToken
                }

                w.write(gson.toJson(authReq))
                w.newLine()
                w.flush()

                listenLoop(r, ip, prefs)
            } catch (e: Exception) {
                handleDisconnect()
            }
        }
    }

    private suspend fun sendPin(pin: String) {
        withContext(Dispatchers.IO) {
            try {
                writer?.let {
                    it.write(gson.toJson(mapOf("pin" to pin)))
                    it.newLine()
                    it.flush()
                }
            } catch (e: Exception) {
                handleDisconnect()
            }
        }
    }

    private suspend fun listenLoop(reader: java.io.BufferedReader, ip: String, prefs: SharedPreferences) {
        try {
            while (currentCoroutineContext().isActive) {
                val line = reader.readLine() ?: break
                val response = gson.fromJson(line, ServerResponse::class.java) ?: continue

                when (response.status) {
                    "unauthorized" -> {
                        connectionState.value = ConnectionState.DISCONNECTED
                        errorFlow.emit("unauthorized")
                    }
                    "ok", "update" -> {
                        if (response.status == "update") configFlow.value = response.config
                        if (!response.token.isNullOrEmpty()) {
                            prefs.edit().apply {
                                putString("device_id", response.device_id)
                                putString("token", response.token)
                                apply()
                            }
                        }
                        connectionState.value = ConnectionState.CONNECTED
                        isRetryJobActive = false
                        startHeartbeat()
                        processUpdate(response)
                    }
                    else -> {
                        if (response.type != null) {
                            processUpdate(response)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            handleDisconnect()
        }
    }

    private suspend fun processUpdate(update: ServerResponse) {
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        when (update.type) {
            "update" -> updatesFlow.emit(update)
            "update_layout" -> configFlow.value = update.config
            "clipboard" -> {
                withContext(Dispatchers.Main) {
                    val clip = ClipData.newPlainText("HyprLink", update.content)
                    cb.setPrimaryClip(clip)
                }
            }
            "media_info" -> {
                updateMediaMetadata(
                    update.content ?: "Unknown",
                    update.app ?: "Arch Linux",
                    update.status == "playing",
                    update.value?.toLong() ?: 0L,
                    update.duration ?: 0L
                )
            }
        }
    }

    private fun updateMediaMetadata(title: String, artist: String, isPlaying: Boolean, position: Long, duration: Long) {
        val speed = if (isPlaying) 1.0f else 0.0f
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED

        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, position, speed) // Передаем скорость для плавной анимации
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackStateCompat.ACTION_SEEK_TO or
                            PlaybackStateCompat.ACTION_STOP or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_PLAY
                )
                .build()
        )

        mediaSession?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration) // Важно для макс. значения ползунка
                .build()
        )

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, createNotification())
    }

    private fun startHeartbeat() {
        serviceScope.launch {
            while (connectionState.value == ConnectionState.CONNECTED) {
                delay(5000)
                try {
                    writer?.let {
                        it.write(gson.toJson(mapOf("type" to "ping")))
                        it.newLine()
                        it.flush()
                    }
                } catch (e: Exception) {
                    handleDisconnect()
                    break
                }
            }
        }
    }

    private fun handleDisconnect() {
        cleanup()
        connectionState.value = ConnectionState.DISCONNECTED
        startRetryLoop()
    }

    private fun cleanup() {
        try {
            socket?.close()
        } catch (e: Exception) {}
        socket = null
        writer = null
    }

    private fun startRetryLoop() {
        if (isRetryJobActive) return
        isRetryJobActive = true
        serviceScope.launch {
            while (connectionState.value == ConnectionState.DISCONNECTED) {
                delay(5000)
                val ip = lastIp
                if (ip != null) connectToServer(ip, lastPort, "")
            }
            isRetryJobActive = false
        }
    }

    fun sendAction(id: String, value: Double?) {
        serviceScope.launch {
            try {
                val action = mutableMapOf<String, Any>("type" to "action", "id" to id)
                if (value != null) action["value"] = value
                writer?.let {
                    it.write(gson.toJson(action))
                    it.newLine()
                    it.flush()
                }
            } catch (e: Exception) {
                handleDisconnect()
            }
        }
    }

    private fun sendNotificationToServer(title: String?, text: String?, app: String?) {
        serviceScope.launch {
            try {
                if (connectionState.value == ConnectionState.CONNECTED) {
                    val payload = mapOf(
                        "type" to "notification",
                        "title" to (title ?: ""),
                        "content" to (text ?: ""),
                        "app" to (app ?: "Android")
                    )
                    writer?.let {
                        it.write(gson.toJson(payload))
                        it.newLine()
                        it.flush()
                    }
                }
            } catch (e: Exception) {
                handleDisconnect()
            }
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        serviceScope.cancel()
        cleanup()
        super.onDestroy()
    }
}