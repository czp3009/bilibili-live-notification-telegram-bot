package com.hiczp.bilibili.notificationTelegrambot

import com.google.gson.GsonBuilder
import java.io.File
import java.util.*

object ApplicationConfig {
    const val CONFIG_FILE_NAME = "config.json"
    private val CONFIG_FILE = File(CONFIG_FILE_NAME)
    private val GSON = GsonBuilder().setPrettyPrinting().serializeNulls().create()

    @Suppress("HasPlatformType")
    lateinit var config: Config

    fun isConfigFileExists() = CONFIG_FILE.exists()

    fun createConfigFile() {
        config = Config().also {
            CONFIG_FILE.run {
                createNewFile()
                writeText(GSON.toJson(it))
            }
        }
    }

    fun writeConfigToDisk() =
            CONFIG_FILE.writeText(GSON.toJson(config))

    fun readConfigFromDisk() {
        config = GSON.fromJson(CONFIG_FILE.readText(), Config::class.java)
    }
}

data class Config(
        val logLevel: String = "INFO",
        val telegramBotConfig: TelegramBotConfig = TelegramBotConfig(),
        val liveRoomIds: List<Long> = Collections.emptyList()
)

data class TelegramBotConfig(
        val username: String = "username",
        val token: String = "token",
        val creatorId: Int = 0,
        val httpProxyConfig: HttpProxyConfig = HttpProxyConfig()
) {
    data class HttpProxyConfig(
            val useHttpProxy: Boolean = false,
            val hostName: String = "localhost",
            val port: Int = 1080,
            val authenticationEnabled: Boolean = false,
            val proxyUser: String = "proxyUser",
            val proxyPassword: String = "proxyPassword"
    )
}
