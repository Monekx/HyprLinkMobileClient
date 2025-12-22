package com.monekx.hyprlink

import com.google.gson.Gson
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DiscoveredServer(
    val hostname: String,
    val ip: String,
    val port: Int
)

class DiscoveryManager {
    private val gson = Gson()

    suspend fun startDiscovery(onServerFound: (DiscoveredServer) -> Unit) = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            // Привязываемся к порту 9999 и разрешаем повторное использование адреса
            socket = DatagramSocket(9999).apply {
                broadcast = true
                reuseAddress = true
            }

            val buffer = ByteArray(1024)

            while (true) {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)

                val message = String(packet.data, 0, packet.length).trim()

                // Простая проверка, что это наш JSON
                if (message.startsWith("{") && message.contains("hostname")) {
                    val beacon = gson.fromJson(message, Beacon::class.java)

                    val server = DiscoveredServer(
                        hostname = beacon.hostname,
                        ip = packet.address.hostAddress,
                        port = beacon.port
                    )

                    withContext(Dispatchers.Main) {
                        onServerFound(server)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            socket?.close()
        }
    }
}

data class Beacon(val hostname: String, val port: Int)