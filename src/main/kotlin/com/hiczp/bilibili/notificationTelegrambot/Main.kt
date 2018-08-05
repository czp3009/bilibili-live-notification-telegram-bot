package com.hiczp.bilibili.notificationTelegrambot

import com.google.common.eventbus.Subscribe
import com.google.gson.JsonSyntaxException
import com.hiczp.bilibili.api.BilibiliAPI
import com.hiczp.bilibili.api.live.socket.LiveClient
import com.hiczp.bilibili.api.live.socket.event.ConnectSucceedEvent
import com.hiczp.bilibili.api.live.socket.event.ConnectionCloseEvent
import com.hiczp.bilibili.api.live.socket.event.CutOffPackageEvent
import com.hiczp.bilibili.api.live.socket.event.ReceiveRoomStatusPackageEvent
import io.netty.channel.nio.NioEventLoopGroup
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.log4j.BasicConfigurator
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.ApiContext
import org.telegram.telegrambots.ApiContextInitializer
import org.telegram.telegrambots.TelegramBotsApi
import org.telegram.telegrambots.bots.DefaultBotOptions
import java.io.IOException
import java.util.concurrent.DelayQueue
import kotlin.concurrent.thread
import kotlin.system.exitProcess

private const val reconnectDelay = 5L

fun main(args: Array<String>) {
    //logger
    BasicConfigurator.configure()
    val logger = LoggerFactory.getLogger("Application")

    //config
    if (ApplicationConfig.isConfigFileExists()) {
        //读取配置文件
        try {
            ApplicationConfig.readConfigFromDisk()
        } catch (e: JsonSyntaxException) {
            e.printStackTrace()
            logger.error("Invalid configuration, modify config file and try again or delete for auto generation")
            exitProcess(-1)
        }
        logger.info("Configuration loaded, log level: ${ApplicationConfig.config.logLevel}")

        //回写配置文件以实现文件内容更新
        Runtime.getRuntime().addShutdownHook(Thread {
            ApplicationConfig.writeConfigToDisk()
        })
    } else {
        logger.error("Config file not exists")
        ApplicationConfig.createConfigFile()
        logger.info("Created new config file '${ApplicationConfig.CONFIG_FILE_NAME}', please edit this file and restart program")
        exitProcess(-2)
    }

    //log level
    Logger.getRootLogger().level = Level.toLevel(ApplicationConfig.config.logLevel)

    //init
    ApiContextInitializer.init()

    //proxy
    val defaultBotOptions = ApiContext.getInstance(DefaultBotOptions::class.java).apply {
        ApplicationConfig.config.telegramBotConfig.httpProxyConfig.run {
            if (!useHttpProxy) return@run
            HttpHost(hostName, port).let {
                requestConfig = RequestConfig.custom().setProxy(it).setAuthenticationEnabled(authenticationEnabled).build()
                httpProxy = it
            }
            if (authenticationEnabled) {
                credentialsProvider = BasicCredentialsProvider().apply {
                    setCredentials(
                            AuthScope(hostName, port),
                            UsernamePasswordCredentials(proxyUser, proxyPassword)
                    )
                }
            }
        }
    }

    //start bot
    logger.info("Connecting to telegram server")
    val notificationBot = ApplicationConfig.config.telegramBotConfig.run {
        NotificationBot(username, token, creatorId, defaultBotOptions).also {
            TelegramBotsApi().registerBot(it)
        }
    }
    logger.info("Connect to telegram server succeed")

    //bilibili
    logger.info("Preparing to connect to bilibili server")
    ApplicationConfig.config.liveRoomIds.run {
        if (isEmpty()) {
            logger.warn("RoomIds not set")
            return@run
        }

        val delayQueue = DelayQueue<DelayedElement<LiveClient>>()
        val eventLoopGroup = NioEventLoopGroup()
        BilibiliAPI().run {
            //重连线程
            thread(true, true, block = {
                while (true) {
                    try {
                        delayQueue.take().element.run {
                            try {
                                connect()
                            } catch (e: IOException) {
                                logger.error(e.toString())
                                eventBus.post(ConnectionCloseEvent(this))
                            }
                        }
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            })

            val failedRoom = ArrayList<LiveClient>()
            forEach {
                getLiveClient(eventLoopGroup, it)
                        .registerListener(
                                object : Any() {
                                    @Suppress("unused")
                                    @Subscribe
                                    fun onConnectSucceed(connectSucceedEvent: ConnectSucceedEvent) {
                                        logger.info("Enter room ${connectSucceedEvent.source0.showRoomIdOrRoomId} succeed")
                                    }

                                    @Suppress("unused")
                                    @Subscribe
                                    fun onRoomStatusChange(receiveRoomStatusPackageEvent: ReceiveRoomStatusPackageEvent<*>) {
                                        notificationBot.sendNotification(receiveRoomStatusPackageEvent.entity.roomId.toLong())
                                    }

                                    @Suppress("unused")
                                    @Subscribe
                                    fun onCutOff(cutOffPackageEvent: CutOffPackageEvent) {
                                        notificationBot.sendNotification(cutOffPackageEvent.entity.roomId)
                                    }

                                    @Suppress("unused")
                                    @Subscribe
                                    fun onDisconnect(connectionCloseEvent: ConnectionCloseEvent) {
                                        connectionCloseEvent.source0.run {
                                            logger.error("Connection to room $showRoomIdOrRoomId lost, we will reconnect after ${reconnectDelay}s")
                                            delayQueue.put(DelayedElement(this, reconnectDelay * 1000))
                                        }
                                    }
                                }
                        ).run {
                            try {
                                connect()
                            } catch (e: IOException) {
                                //如果一开始就连不上(此时 Telegram 已连接), 将连不上的房间记录下来然后投入重连队列
                                failedRoom.add(this)
                                logger.error("Connect to room $it failed: ${e.message}, we will reconnect after all connection complete")
                            }
                        }
            }

            //对第一次没连上的房间进行重连
            failedRoom.forEach {
                delayQueue.put(DelayedElement(it, 0))
            }
        }
    }
}
