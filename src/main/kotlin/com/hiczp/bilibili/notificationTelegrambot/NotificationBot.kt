package com.hiczp.bilibili.notificationTelegrambot

import com.hiczp.bilibili.api.BilibiliAPI
import com.hiczp.bilibili.api.ServerErrorCode
import com.hiczp.bilibili.api.live.entity.LiveRoomInfoEntity
import org.slf4j.LoggerFactory
import org.telegram.abilitybots.api.bot.AbilityBot
import org.telegram.abilitybots.api.objects.Ability
import org.telegram.abilitybots.api.objects.Locality
import org.telegram.abilitybots.api.objects.Privacy
import org.telegram.telegrambots.api.methods.BotApiMethod
import org.telegram.telegrambots.api.methods.send.SendMessage
import org.telegram.telegrambots.api.objects.Message
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.exceptions.TelegramApiRequestException
import org.telegram.telegrambots.updateshandlers.SentCallback
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.lang.Exception

class NotificationBot(username: String,
                      token: String,
                      private val creatorId: Int,
                      defaultBotOptions: DefaultBotOptions) : AbilityBot(token, username, defaultBotOptions) {
    override fun creatorId() = creatorId

    @Suppress("unused", "HasPlatformType")
    fun enable() =
            Ability.builder()
                    .name("enable")
                    .info("enable notification push in this chat group")
                    .locality(Locality.GROUP)
                    .privacy(Privacy.GROUP_ADMIN)
                    .action {
                        db.run {
                            getSet<Long>(ENABLED_GROUP).add(it.chatId())
                            commit()    //修复由于强制中断程序而导致的一部分数据未存入磁盘的问题 https://github.com/czp3009/bilibili-live-notification-telegram-bot/issues/2
                            logger.info("Enabled notification in group ${it.chatId()}")
                        }
                        silent.send("Enabled notification push", it.chatId())
                    }
                    .build()

    @Suppress("unused", "HasPlatformType")
    fun disable() =
            Ability.builder()
                    .name("disable")
                    .info("disable notification push in this chat group")
                    .locality(Locality.GROUP)
                    .privacy(Privacy.GROUP_ADMIN)
                    .action {
                        db.run {
                            getSet<Long>(ENABLED_GROUP).remove(it.chatId())
                            commit()
                            logger.info("Disabled notification in group ${it.chatId()}")
                        }
                        silent.send("Disabled notification push", it.chatId())
                    }
                    .build()

    @Suppress("unused", "HasPlatformType")
    fun room() =
            Ability.builder()
                    .name("room")
                    .input(1)
                    .info("fetch info of target room")
                    .locality(Locality.ALL)
                    .privacy(Privacy.PUBLIC)
                    .action {
                        val chatId = it.chatId()
                        try {
                            it.firstArg().toLong()
                        } catch (e: NumberFormatException) {
                            executeAsync(SendMessage(chatId, "Argument roomId must be a valid number"), NormalSentCallback(it))
                            return@action
                        }.run {
                            logger.info("User ${it.user().username()} toggle 'room' command")
                            liveService.getRoomInfo(this).enqueue(
                                    object : Callback<LiveRoomInfoEntity> {
                                        override fun onResponse(call: Call<LiveRoomInfoEntity>, response: Response<LiveRoomInfoEntity>) {
                                            if (!response.isSuccessful) {
                                                "Fetch room info failed: ${response.message()}"
                                            } else {
                                                response.body()?.run {
                                                    when (code) {
                                                        ServerErrorCode.Common.OK -> {
                                                            this.data.run {
                                                                StringBuilder()
                                                                        .appendln("Title: $title")
                                                                        .appendln("HostName: $username")
                                                                        .appendln("Category: ${buildCategoryString(this)}")
                                                                        .appendln("Status: $status")
                                                                        .appendln(buildUrlString(showRoomId))
                                                                        .toString()
                                                            }
                                                        }
                                                        ServerErrorCode.Live.DOCUMENT_IS_NOT_EXISTS -> "Target room not exists"
                                                        else -> "Fetch room info failed: $message"
                                                    }
                                                } ?: "[ERROR] Server return empty body"
                                            }.run {
                                                silent.send(this, chatId)
                                            }
                                        }

                                        override fun onFailure(call: Call<LiveRoomInfoEntity>, t: Throwable) {
                                            t.printStackTrace()
                                            executeAsync(SendMessage(chatId, "[ERROR] ${t.message}"), NormalSentCallback(it))
                                        }
                                    }
                            )
                        }
                    }
                    .build()

    fun sendNotification(roomId: Long) {
        logger.info("Room $roomId changed status")
        db.getSet<Long>(ENABLED_GROUP).run {
            if (isEmpty()) {
                logger.warn("No group enabled notification")
                return
            }
            logger.info("Preparing send notification to $size groups")
            liveService.getRoomInfo(roomId).enqueue(
                    object : Callback<LiveRoomInfoEntity> {
                        override fun onResponse(call: Call<LiveRoomInfoEntity>, response: Response<LiveRoomInfoEntity>) {
                            if (!response.isSuccessful) {
                                logger.error("fetch room info failed: ${response.message()}")
                                return
                            }
                            response.body()?.run {
                                if (code != ServerErrorCode.Common.OK) {
                                    logger.error("fetch room info failed: $message")
                                    return
                                }
                                data.run {
                                    StringBuilder()
                                            .appendln("Room $showRoomId changed status to $status")
                                            .appendln("Title: $title")
                                            .appendln("HostName: $username")
                                            .appendln("Category: ${buildCategoryString(this)}")
                                            .appendln(buildUrlString(showRoomId))
                                            .toString()
                                            .let { message ->
                                                forEach {
                                                    executeAsync(
                                                            SendMessage(it, message),
                                                            object : SentCallback<Message> {
                                                                override fun onResult(method: BotApiMethod<Message>, response: Message) {
                                                                    logger.info("Send notification to group $it succeed")
                                                                }

                                                                override fun onException(method: BotApiMethod<Message>, exception: Exception) {
                                                                    exception.printStackTrace()
                                                                    logger.error("Error occurred when push notification to chat group $it")
                                                                }

                                                                override fun onError(method: BotApiMethod<Message>, apiException: TelegramApiRequestException) {
                                                                    logger.warn(apiException.message)
                                                                }
                                                            }
                                                    )
                                                }
                                            }
                                }
                            } ?: logger.error("server return empty body")
                        }

                        override fun onFailure(call: Call<LiveRoomInfoEntity>, t: Throwable) {
                            t.printStackTrace()
                        }
                    }
            )
        }
    }

    private fun buildCategoryString(liveRoom: LiveRoomInfoEntity.LiveRoom) =
            liveRoom.run {
                StringBuilder().run {
                    append(area)
                    areaV2ParentName.run {
                        if (isNotEmpty()) append(" / $this")
                    }
                    areaV2Name.run {
                        if (isNotEmpty()) append(" / $this")
                    }
                    toString()
                }
            }

    private fun buildUrlString(roomId: Long) =
            LIVE_BASE_URL + roomId

    companion object {
        private const val ENABLED_GROUP = "ENABLED_GROUP"
        private const val LIVE_BASE_URL = "https://live.bilibili.com/"
        private val logger = LoggerFactory.getLogger(NotificationBot::class.java)
        private val liveService = BilibiliAPI().liveService
    }
}
