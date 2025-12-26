# NSD Empty Attribute Key Fix

## 问题描述

在NSD服务发现过程中，遇到以下错误：

```
E  Invalid attribute (Explain with AI)
java.lang.IllegalArgumentException: Key cannot be empty
at android.net.nsd.NsdServiceInfo.setAttribute(NsdServiceInfo.java:233)
```

## 错误分析

### 根本原因
服务端在注册mDNS服务时，TXT记录中包含了**空键名**的属性，导致Android NSD系统抛出异常。

### 错误场景
1. **服务注册端**：服务端注册mDNS服务时，TXT记录格式不正确
2. **客户端解析**：客户端解析服务属性时，没有过滤空键名
3. **系统异常**：Android NSD系统检测到空键名并抛出异常

### 日志分析
```
Service monitoring resolved: Taplink-Server-P303D3BM10048, 10.14.79.125:8443
```
- 服务发现和解析成功
- 但在处理服务属性时失败

## 解决方案

### 1. 客户端防护措施

#### 修复 DeviceDiscoveryManager.extractAttributes()
```kotlin
private fun extractAttributes(serviceInfo: NsdServiceInfo): Map<String, String> {
    return try {
        val attributes = mutableMapOf<String, String>()
        
        serviceInfo.attributes?.let { attrs ->
            for ((key, value) in attrs) {
                // 过滤空键名和空值，避免 IllegalArgumentException
                if (key != null && key.isNotEmpty() && value != null) {
                    try {
                        val valueStr = String(value, Charsets.UTF_8)
                        attributes[key] = valueStr
                        LogUtil.d(TAG, "Extracted attribute: $key = $valueStr")
                    } catch (e: Exception) {
                        LogUtil.w(TAG, "Failed to decode attribute value for key '$key': ${e.message}")
                    }
                } else {
                    LogUtil.w(TAG, "Skipping invalid attribute: key='$key', value=${if (value != null) "non-null" else "null"}")
                }
            }
        }
        
        LogUtil.d(TAG, "Successfully extracted ${attributes.size} valid attributes")
        attributes
        
    } catch (e: Exception) {
        LogUtil.e(TAG, "Failed to extract attributes: ${e.message}")
        emptyMap()
    }
}
```

#### 修复 WebSocketClientKernel.logServiceAttributes()
```kotlin
private fun logServiceAttributes(serviceInfo: NsdServiceInfo) {
    try {
        val attributes = serviceInfo.attributes
        if (attributes != null && attributes.isNotEmpty()) {
            LogUtil.d(TAG, "Service ${serviceInfo.serviceName} attributes:")
            for ((key, value) in attributes) {
                // 安全处理空键名和空值
                if (key != null && key.isNotEmpty()) {
                    val valueStr = if (value != null) {
                        try {
                            String(value, Charsets.UTF_8)
                        } catch (e: Exception) {
                            "decode_error:${e.message}"
                        }
                    } else {
                        "null"
                    }
                    LogUtil.d(TAG, "  '$key' = '$valueStr'")
                } else {
                    LogUtil.w(TAG, "  [INVALID] empty/null key with value: ${if (value != null) "non-null" else "null"}")
                }
            }
        } else {
            LogUtil.d(TAG, "Service ${serviceInfo.serviceName} has no attributes")
        }
    } catch (e: Exception) {
        LogUtil.e(TAG, "Error logging service attributes: ${e.message}")
    }
}
```

### 2. 服务端修复建议

服务端在注册mDNS服务时，应确保：

1. **TXT记录格式正确**：
   ```
   正确：deviceId=P303D3BM10048
   错误：=somevalue 或 空键名
   ```

2. **属性验证**：
   ```kotlin
   // 服务端注册时验证属性
   fun validateAttributes(attributes: Map<String, String>): Map<String, String> {
       return attributes.filter { (key, value) ->
           key.isNotEmpty() && value.isNotEmpty()
       }
   }
   ```

3. **标准TXT记录格式**：
   ```
   deviceId=P303D3BM10048
   version=1.0
   protocol=taplink
   ```

## 修复效果

### 修复前
- 遇到空键名属性时抛出异常
- 服务发现失败
- 无法建立连接

### 修复后
- 自动过滤无效属性
- 记录警告日志便于调试
- 服务发现继续正常进行
- 连接建立成功

## 测试验证

### 1. 单元测试
```kotlin
@Test
fun `test service attributes with empty keys`() {
    val serviceInfo = mockk<NsdServiceInfo>()
    val attributes = mapOf(
        "deviceId" to "P303D3BM10048".toByteArray(),
        "" to "invalid".toByteArray(),  // 空键名
        "version" to "1.0".toByteArray()
    )
    every { serviceInfo.attributes } returns attributes
    
    val result = extractAttributes(serviceInfo)
    
    // 应该只包含有效属性
    assertEquals(2, result.size)
    assertEquals("P303D3BM10048", result["deviceId"])
    assertEquals("1.0", result["version"])
    assertNull(result[""])  // 空键名应被过滤
}
```

### 2. 集成测试
1. 使用包含空键名的服务进行发现测试
2. 验证服务发现不会因属性问题而失败
3. 确认连接可以正常建立

## 预防措施

### 1. 服务端规范
- 制定TXT记录格式规范
- 实施属性验证机制
- 添加服务注册测试

### 2. 客户端防护
- 所有属性解析都添加空键名检查
- 增强错误处理和日志记录
- 实施属性格式验证

### 3. 监控和调试
- 添加详细的属性解析日志
- 监控属性解析异常
- 提供诊断工具

## 相关文件

- `lib_taplink_communication/src/main/java/com/sunmi/tapro/taplink/communication/lan/DeviceDiscoveryManager.kt`
- `lib_taplink_communication/src/main/java/com/sunmi/tapro/taplink/communication/lan/WebSocketClientKernel.kt`
- `lib_taplink_communication/src/test/java/com/sunmi/tapro/taplink/communication/NsdServiceDiscoveryTest.kt`

## 总结

这个修复解决了NSD服务发现中因空键名属性导致的异常问题，通过在客户端添加防护措施，确保服务发现的稳定性和可靠性。同时建议服务端也进行相应的规范化改进。