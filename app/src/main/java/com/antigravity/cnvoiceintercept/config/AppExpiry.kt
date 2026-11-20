package com.antigravity.cnvoiceintercept.config

import java.time.ZoneId
import java.time.ZonedDateTime

object AppExpiry {
    private val EXPIRY_EPOCH_MS: Long by lazy {
        ZonedDateTime.of(2026, 12, 2, 0, 0, 0, 0, ZoneId.of("Asia/Taipei"))
            .toInstant()
            .toEpochMilli()
    }

    fun isExpired(): Boolean = System.currentTimeMillis() >= EXPIRY_EPOCH_MS
}
