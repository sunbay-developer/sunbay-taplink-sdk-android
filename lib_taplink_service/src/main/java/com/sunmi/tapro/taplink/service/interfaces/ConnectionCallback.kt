package com.sunmi.tapro.taplink.service.interfaces

interface ConnectionCallback {
    fun onConnected(extraInfoMap: Map<String, String?>?)
    fun onWaitingConnect()
    fun onDisconnected(code: String, msg: String)
}