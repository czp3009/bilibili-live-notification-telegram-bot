package com.hiczp.bilibili.notificationTelegrambot

import com.google.common.eventbus.Subscribe
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
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.ApiContext
import org.telegram.telegrambots.ApiContextInitializer
import org.telegram.telegrambots.TelegramBotsApi
import org.telegram.telegrambots.bots.DefaultBotOptions
import java.io.IOException
import java.util.concurrent.DelayQueue
import kotlin.concurrent.thread

private const val reconnectDelay = 5L

fun main(args: Array<String>) {
    //logger
    BasicConfigurator.configure()
    val logger = LoggerFactory.getLogger("Application")

    //init
    ApiContextInitializer.init()

    //proxy
    val defaultBotOptions = ApiContext.getInstance(DefaultBotOptions::class.java).apply {
        ApplicationConfig.telegramBotConfig.httpProxyConfig.run {
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
    val notificationBot = ApplicationConfig.telegramBotConfig.run {
        NotificationBot(username, token, creatorId, defaultBotOptions).also {
            TelegramBotsApi().registerBot(it)
        }
    }
    logger.info("Connect to telegram server succeed")

    //bilibili
    logger.info("Preparing to connect to bilibili server")
    ApplicationConfig.liveRoomIds.run {
        if (isEmpty()) {
            logger.warn("RoomIds not set")
            return@run
        }

        val delayQueue = DelayQueue<DelayedElement<LiveClient>>()
        val eventLoopGroup = NioEventLoopGroup()
        BilibiliAPI().run {
            forEach {
                getLiveClient(eventLoopGroup, it)
                        .registerListener(
                                object : Any() {
                                    @Subscribe
                                    fun onConnectSucceed(connectSucceedEvent: ConnectSucceedEvent) {
                                        logger.info("Enter room ${connectSucceedEvent.source0.showRoomIdOrRoomId} succeed")
                                    }

                                    @Subscribe
                                    fun onRoomStatusChange(receiveRoomStatusPackageEvent: ReceiveRoomStatusPackageEvent<*>) {
                                        notificationBot.sendNotification(receiveRoomStatusPackageEvent.entity.roomId.toLong())
                                    }

                                    @Subscribe
                                    fun onCutOff(cutOffPackageEvent: CutOffPackageEvent) {
                                        notificationBot.sendNotification(cutOffPackageEvent.entity.roomId.toLong())
                                    }

                                    @Subscribe
                                    fun onDisconnect(connectionCloseEvent: ConnectionCloseEvent) {
                                        connectionCloseEvent.source0.run {
                                            logger.error("Connection to room $showRoomIdOrRoomId lost, we will reconnect after ${reconnectDelay}s")
                                            delayQueue.put(DelayedElement(this, reconnectDelay * 1000))
                                        }
                                    }
                                }
                        )
                        .connect()
            }
        }

        //重连线程
        thread(true, true, block = {
            while (true) {
                try {
                    delayQueue.take().element.run {
                        try {
                            connect()
                        } catch (e: IOException) {
                            logger.error(e.message)
                            eventBus.post(ConnectionCloseEvent(this))
                        }
                    }
                } catch (e: InterruptedException) {
                    break
                }
            }
        })
    }
}
