package com.hiczp.bilibili.notificationTelegrambot

import com.google.gson.GsonBuilder
import org.slf4j.LoggerFactory
import java.nio.file.Paths
import java.util.*

object ApplicationConfig {
    private const val CONFIG_FILE_NAME = "config.json"
    private val logger = LoggerFactory.getLogger(ApplicationConfig::class.java)
    private val GSON = GsonBuilder().setPrettyPrinting().create()

    private val config = lazy { readConfigFromDisk() }

    val telegramBotConfig = config.value.telegramBotConfig
    val liveRoomIds = config.value.liveRoomIds

    private fun readConfigFromDisk() =
            Paths.get(CONFIG_FILE_NAME).toFile().run {
                if (exists()) {
                    GSON.fromJson(readText(), Config::class.java)
                } else {
                    logger.info("Config file not exists, creating new one")
                    Config().also {
                        createNewFile()
                        writeText(GSON.toJson(it))
                    }
                }
            }
}

private data class Config(
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
