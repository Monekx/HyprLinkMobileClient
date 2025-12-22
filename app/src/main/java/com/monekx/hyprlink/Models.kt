package com.monekx.hyprlink

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

data class UIConfig(
    val hostname: String,
    val hash: String,
    val profiles: List<ProfileTab>,
    val css: String? = null
)

data class ProfileTab(
    val name: String,
    val modules: List<Module>
)

data class Module(
    val id: String,
    val type: String,
    val label: String? = null,
    val view: String? = null,
    val icon: String? = null,
    val children: List<Module>? = null,
    val action: String? = null,
    val source: String? = null
)

data class ServerResponse(
    val status: String? = null,
    val config: UIConfig? = null,
    val message: String? = null,
    val app: String? = null, // Добавьте эту строку
    val type: String? = null,
    val id: String? = null,
    val value: Double? = null,
    val title: String? = null,
    val content: String? = null,
    val device_id: String? = null,
    val token: String?,
    val duration: Long? = null,
)