package com.sunmi.tapro.taplink.communication.interfaces

interface ConnectionCallback {
    fun onConnected(extraInfoMap: Map<String, String?>?)
    fun onWaitingConnect()
    fun onDisconnected(code: String, msg: String)

    /**
     * 当检测到服务地址变更时调用
     *
     * @param newHost 新的主机地址
     * @param newPort 新的端口号
     * @param oldHost 旧的主机地址
     * @param oldPort 旧的端口号
     */
    fun onServiceAddressChanged(nameService: String, newHost: String, newPort: Int, oldHost: String, oldPort: Int): Boolean {
        // 默认空实现，子类可选择性重写
        return false
    }
}