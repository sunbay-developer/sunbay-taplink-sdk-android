package com.sunmi.tapro.taplink.sdk.impl

import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest

/** Capture caller-owned mutable fields at SDK ownership boundaries, not at each later read. */
internal fun TaplinkConfig.snapshotForInitialization(): TaplinkConfig = copy(
    defaultStaffInfo = defaultStaffInfo?.copy(),
    defaultDeviceInfo = defaultDeviceInfo?.copy()
)

internal fun PaymentRequest.snapshotForExecution(): PaymentRequest = copy(
    amount = amount?.copy(),
    paymentMethod = paymentMethod?.copy(),
    staffInfo = staffInfo?.copy(),
    deviceInfo = deviceInfo?.copy(),
    goodsDetail = goodsDetail?.map { it.copy() },
    tipConfig = tipConfig?.let { config ->
        config.copy(suggestions = config.suggestions?.let { suggestions ->
            suggestions.copy(values = suggestions.values.toList(), names = suggestions.names?.toList())
        })
    }
)
