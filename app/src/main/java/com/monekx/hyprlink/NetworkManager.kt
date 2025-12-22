package com.monekx.hyprlink

import java.net.Socket
import com.google.gson.Gson

class NetworkManager {
    private val gson = Gson()

    fun connectToArch(ip: String, port: Int, pin: String, currentHash: String): ServerResponse? {
        return try {
            val socket = Socket(ip, port)
            val writer = socket.getOutputStream().bufferedWriter()
            val reader = socket.getInputStream().bufferedReader()

            // 1. Handshake
            val authReq = mapOf("pin" to pin, "hash" to currentHash)
            writer.write(gson.toJson(authReq))
            writer.newLine()
            writer.flush()

            // 2. Response
            val responseLine = reader.readLine()
            gson.fromJson(responseLine, ServerResponse::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun sendAction(socket: Socket, id: String) {
        Thread {
            try {
                val writer = socket.getOutputStream().bufferedWriter()
                val action = mapOf("type" to "action", "id" to id)
                writer.write(gson.toJson(action))
                writer.newLine()
                writer.flush()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}