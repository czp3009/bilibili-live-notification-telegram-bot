package com.hiczp.bilibili.notificationTelegrambot

import java.util.concurrent.Delayed
import java.util.concurrent.TimeUnit

class DelayedElement<out T>(val element: T, private val delay: Long) : Delayed {
    private val expire = System.currentTimeMillis() + delay

    override fun compareTo(other: Delayed): Int = (getDelay(TimeUnit.MILLISECONDS) - other.getDelay(TimeUnit.MILLISECONDS)).toInt()

    override fun getDelay(unit: TimeUnit): Long =
            unit.convert(
                    expire - System.currentTimeMillis(),
                    TimeUnit.MILLISECONDS
            )
}
