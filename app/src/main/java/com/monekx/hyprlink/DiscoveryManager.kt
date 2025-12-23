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
    private var isBroadcasting = true

    suspend fun startBroadcasting(
        hostname: String,
        tcpPort: Int,
        onServerFound: (DiscoveredServer) -> Unit
    ) = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        isBroadcasting = true

        try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = 1500 // Таймаут для receive
            }

            val beacon = Beacon(hostname, tcpPort)
            val msg = gson.toJson(beacon).toByteArray()
            val packet = DatagramPacket(msg, msg.size, InetAddress.getByName("255.255.255.255"), 9999)

            while (isBroadcasting) {
                // Отправляем маяк
                socket.send(packet)

                // Пытаемся поймать ACK от Arch
                val ackBuf = ByteArray(64)
                val ackPacket = DatagramPacket(ackBuf, ackBuf.size)

                try {
                    socket.receive(ackPacket)
                    val resp = String(ackPacket.data, 0, ackPacket.length).trim()

                    if (resp.startsWith("HYPRLINK_ACK")) {
                        val serverIp = ackPacket.address.hostAddress
                        val serverPort = resp.split("|").getOrNull(1)?.toInt() ?: tcpPort

                        Log.d("Discovery", "Arch found us at $serverIp. Stopping UDP.")
                        isBroadcasting = false

                        withContext(Dispatchers.Main) {
                            onServerFound(DiscoveredServer("Arch Host", serverIp, serverPort))
                        }
                    }
                } catch (e: Exception) {
                    // Просто таймаут, продолжаем цикл
                }

                if (isBroadcasting) delay(2000)
            }
        } finally {
            socket?.close()
        }
    }
}