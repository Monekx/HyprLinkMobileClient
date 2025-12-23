package com.monekx.hyprlink

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class DiscoveryManager {
    private val gson = Gson()
    private var isRunning = true
    private var isConnected = false
    private var currentInterval = 2000L // Начальный интервал 2 секунды

    fun setConnected(status: Boolean) {
        isConnected = status
        if (!status) {
            currentInterval = 2000L // Сброс на быстрый поиск при дисконнекте
            isRunning = true
        }
    }

    suspend fun startBroadcasting(
        hostname: String,
        tcpPort: Int,
        onServerFound: (DiscoveredServer) -> Unit
    ) = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        isRunning = true

        try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = 1500
            }

            val beacon = Beacon(hostname, tcpPort)
            val msg = gson.toJson(beacon).toByteArray()
            val packet = DatagramPacket(msg, msg.size, InetAddress.getByName("255.255.255.255"), 9999)

            while (isRunning) {
                if (!isConnected) {
                    socket.send(packet)

                    val ackBuf = ByteArray(64)
                    val ackPacket = DatagramPacket(ackBuf, ackBuf.size)

                    try {
                        socket.receive(ackPacket)
                        val resp = String(ackPacket.data, 0, ackPacket.length).trim()

                        if (resp.startsWith("HYPRLINK_ACK")) {
                            val serverIp = ackPacket.address.hostAddress
                            val serverPort = resp.split("|").getOrNull(1)?.toInt() ?: tcpPort

                            // Увеличиваем интервал после обнаружения
                            currentInterval = 30000L

                            withContext(Dispatchers.Main) {
                                onServerFound(DiscoveredServer("Arch Host", serverIp, serverPort))
                            }
                        }
                    } catch (e: Exception) {
                        // Таймаут
                    }
                }

                // Если коннект успешен — полностью спим, пока не сбросят isRunning/isConnected
                if (isConnected) {
                    delay(5000)
                } else {
                    delay(currentInterval)
                }
            }
        } finally {
            socket?.close()
        }
    }

    fun stopFull() {
        isRunning = false
    }
}