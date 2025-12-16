# LibTaplinkCommunication rules
-keep class com.sunmi.tapro.taplink.communication.** { *; }
-keep class com.sunmi.tapro.taplink.communication.lan.** { *; }
-keep class com.sunmi.tapro.taplink.communication.usb.** { *; }
-keep class com.sunmi.tapro.taplink.communication.serial.** { *; }

# OkHttp WebSocket
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

# Java-WebSocket Library (for WebSocketServer and WebSocketClient)
-keep class org.java_websocket.** { *; }
-dontwarn org.java_websocket.**
-keep interface org.java_websocket.WebSocket { *; }
-keep class org.java_websocket.client.WebSocketClient { *; }
-keep class org.java_websocket.server.WebSocketServer { *; }
-keep class org.java_websocket.handshake.** { *; }

# Note: More detailed LAN service rules can be found in:
# src/main/java/com/sunmi/tapro/taplink/service/lan/lan-rules.pro



