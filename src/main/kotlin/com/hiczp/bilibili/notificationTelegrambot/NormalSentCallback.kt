package com.hiczp.bilibili.notificationTelegrambot

import org.slf4j.LoggerFactory
import org.telegram.abilitybots.api.objects.MessageContext
import org.telegram.telegrambots.api.methods.BotApiMethod
import org.telegram.telegrambots.api.objects.Message
import org.telegram.telegrambots.exceptions.TelegramApiRequestException
import org.telegram.telegrambots.updateshandlers.SentCallback

class NormalSentCallback(private val messageContext: MessageContext) : SentCallback<Message> {
    override fun onResult(method: BotApiMethod<Message>, response: Message) {

    }

    override fun onException(method: BotApiMethod<Message>, exception: Exception) {
        exception.printStackTrace()
        logger.error("Error occurred when response to user ${messageContext.user().username()}")
    }

    override fun onError(method: BotApiMethod<Message>, apiException: TelegramApiRequestException) {
        logger.warn(apiException.message)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NormalSentCallback::class.java)
    }
}
