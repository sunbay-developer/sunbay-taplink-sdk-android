# 代码生成规则和最佳实践

## 📋 概述

本文档定义了 Taplink SDK 项目的代码生成规则和最佳实践，确保代码质量和一致性。

## 🎯 核心原则

### 1. 类型安全优先

**✅ 使用枚举代替魔法字符串**

```kotlin
// ✅ 推荐：使用枚举
val request = PaymentRequest(
    action = TransactionAction.SALE.value,
    ...
)

// ❌ 避免：魔法字符串
val request = PaymentRequest(
    action = "SALE",
    ...
)
```

**原因：**
- 编译时类型检查
- IDE 自动提示
- 重构友好
- 避免拼写错误

### 2. 向后兼容

**✅ 保持 API 兼容性**

```kotlin
// 新增方法时，保留旧方法
fun setAction(action: TransactionAction): PaymentRequest  // 新增
fun setAction(action: String): PaymentRequest             // 保留
```

**原则：**
- 不删除公共 API
- 使用 `@Deprecated` 标记过时方法
- 提供迁移路径

### 3. 职责分离

**SDK 层职责：**
- 业务逻辑
- 状态管理
- 重连管理
- 用户 API

**Service 层职责：**
- 底层通信
- 心跳检测
- 数据传输
- 服务发现

```kotlin
// ✅ SDK 层管理重连
class ReconnectManager {
    private var isManualDisconnect = false
    fun onDisconnected(): Boolean { ... }
}

// ✅ Service 层只通知状态
class WebSocketClientKernel {
    override fun onClose() {
        callback?.onDisconnected(code, reason)
    }
}
```

## 📝 命名规范

### 枚举类

```kotlin
// ✅ 使用 PascalCase，名词形式
enum class TransactionAction { ... }
enum class ConnectionMode { ... }
enum class PaymentEvent { ... }

// ❌ 避免
enum class transactionAction { ... }  // 小写开头
enum class TransactionActions { ... } // 复数形式
```

### 枚举值

```kotlin
// ✅ 使用 UPPER_SNAKE_CASE
enum class TransactionAction {
    SALE,
    POST_AUTH,
    INCREMENT_AUTH
}

// ❌ 避免
enum class TransactionAction {
    sale,           // 小写
    postAuth,       // camelCase
    IncrementAuth   // PascalCase
}
```

### 常量

```kotlin
// ✅ 使用 UPPER_SNAKE_CASE
companion object {
    private const val DEFAULT_TIMEOUT = 60_000L
    private const val MAX_RETRY_COUNT = 5
}

// ❌ 避免
companion object {
    private const val defaultTimeout = 60_000L  // camelCase
    private const val MaxRetryCount = 5         // PascalCase
}
```

## 🔧 代码模式

### 1. 枚举定义模式

```kotlin
/**
 * 枚举说明
 *
 * @author TaPro Team
 * @since YYYY-MM-DD
 */
enum class EnumName(val value: String) {
    /**
     * 枚举值说明
     */
    VALUE_1("VALUE_1"),
    
    /**
     * 枚举值说明
     */
    VALUE_2("VALUE_2");
    
    companion object {
        /**
         * 从字符串值获取枚举（安全）
         */
        @JvmStatic
        fun fromValue(value: String): EnumName? {
            return values().find { it.value.equals(value, ignoreCase = true) }
        }
        
        /**
         * 从字符串值获取枚举（抛出异常）
         */
        @JvmStatic
        fun valueOf(value: String): EnumName {
            return fromValue(value)
                ?: throw IllegalArgumentException("Unknown value: $value")
        }
        
        /**
         * 获取所有值
         */
        @JvmStatic
        fun getAllValues(): List<String> {
            return values().map { it.value }
        }
    }
    
    override fun toString(): String = value
}
```

### 2. 数据类模式

```kotlin
/**
 * 数据类说明
 *
 * @author TaPro Team
 * @since YYYY-MM-DD
 */
data class DataClassName(
    /**
     * 字段说明
     */
    val field1: String,
    
    /**
     * 字段说明（可选）
     */
    val field2: String? = null
) {
    /**
     * 链式调用方法
     */
    fun setField1(value: String): DataClassName = copy(field1 = value)
    
    companion object {
        /**
         * 创建构建器
         */
        @JvmStatic
        fun builder(): Builder = Builder()
    }
    
    /**
     * 构建器
     */
    class Builder {
        private var field1: String = ""
        private var field2: String? = null
        
        fun setField1(value: String) = apply { this.field1 = value }
        fun setField2(value: String) = apply { this.field2 = value }
        
        fun build(): DataClassName {
            require(field1.isNotEmpty()) { "field1 is required" }
            return DataClassName(field1, field2)
        }
    }
}
```

### 3. 回调接口模式

```kotlin
/**
 * 回调接口说明
 *
 * @author TaPro Team
 * @since YYYY-MM-DD
 */
interface CallbackName {
    /**
     * 成功回调
     */
    fun onSuccess(result: ResultType)
    
    /**
     * 失败回调
     */
    fun onFailure(error: ErrorType)
    
    /**
     * 进度回调（可选）
     */
    fun onProgress(event: EventType) {
        // 默认实现（可选）
    }
}
```

## 📚 文档规范

### 类文档

```kotlin
/**
 * 类的简短描述（一句话）
 *
 * 详细说明：
 * - 功能点 1
 * - 功能点 2
 * - 功能点 3
 *
 * 使用示例：
 * ```kotlin
 * val example = ClassName(...)
 * example.method()
 * ```
 *
 * @param param1 参数说明
 * @param param2 参数说明
 *
 * @author TaPro Team
 * @since YYYY-MM-DD
 */
class ClassName(
    private val param1: String,
    private val param2: Int
) {
    // ...
}
```

### 方法文档

```kotlin
/**
 * 方法的简短描述（一句话）
 *
 * 详细说明（如果需要）
 *
 * @param param1 参数说明
 * @param param2 参数说明
 * @return 返回值说明
 * @throws ExceptionType 异常说明
 */
fun methodName(param1: String, param2: Int): ReturnType {
    // ...
}
```

### 字段文档

```kotlin
/**
 * 字段说明
 *
 * 详细说明（如果需要）
 */
private val fieldName: String
```

## 🎨 代码风格

### 1. 缩进和格式

```kotlin
// ✅ 使用 4 空格缩进
class ClassName {
    fun method() {
        if (condition) {
            doSomething()
        }
    }
}

// ✅ 链式调用换行对齐
val request = PaymentRequest.builder()
    .setAction(TransactionAction.SALE)
    .setReferenceOrderId("ORDER001")
    .setTransactionRequestId("TXN001")
    .build()
```

### 2. 导入顺序

```kotlin
// 1. Android 导入
import android.content.Context
import android.util.Log

// 2. 第三方库导入
import com.google.gson.Gson

// 3. 项目内部导入
import com.sunmi.tapro.taplink.sdk.api.TaplinkApi
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest
```

### 3. 空行规则

```kotlin
class ClassName {
    // 字段定义
    private val field1: String
    private val field2: Int
    
    // 空一行
    
    // 初始化块
    init {
        // ...
    }
    
    // 空一行
    
    // 公共方法
    fun publicMethod() {
        // ...
    }
    
    // 空一行
    
    // 私有方法
    private fun privateMethod() {
        // ...
    }
}
```

## ✅ 代码检查清单

### 提交前检查

- [ ] 所有公共 API 都有文档注释
- [ ] 使用枚举代替魔法字符串
- [ ] 保持向后兼容性
- [ ] 遵循命名规范
- [ ] 代码格式正确
- [ ] 无编译错误和警告
- [ ] 通过单元测试

### 代码审查检查

- [ ] 职责划分清晰
- [ ] 错误处理完善
- [ ] 日志记录适当
- [ ] 资源管理正确
- [ ] 线程安全
- [ ] 性能考虑

## 🔍 常见问题

### Q1: 何时使用枚举？

**A:** 当有固定的、有限的值集合时，应该使用枚举。例如：
- 交易类型（SALE, REFUND, VOID...）
- 连接模式（USB, LAN, APP_TO_APP...）
- 连接状态（CONNECTED, DISCONNECTED...）

### Q2: 如何保持向后兼容？

**A:** 
1. 不删除公共 API
2. 使用 `@Deprecated` 标记过时方法
3. 提供新旧方法的重载
4. 在文档中说明迁移路径

### Q3: 何时使用 data class？

**A:** 当类主要用于存储数据时，使用 data class。它会自动生成：
- `equals()` 和 `hashCode()`
- `toString()`
- `copy()`
- `componentN()` 解构函数

### Q4: 如何处理可选参数？

**A:** 
1. 使用默认参数值
2. 使用可空类型 `Type?`
3. 提供 Builder 模式

## 📊 代码质量指标

### 目标指标

| 指标 | 目标值 |
|------|--------|
| 代码覆盖率 | ≥ 80% |
| 文档覆盖率 | 100% (公共 API) |
| 编译警告 | 0 |
| 代码重复率 | ≤ 5% |
| 圈复杂度 | ≤ 10 |

## 🎉 总结

遵循这些规则和最佳实践，可以：

1. ✅ 提高代码质量
2. ✅ 增强可维护性
3. ✅ 改善开发体验
4. ✅ 减少错误和 bug
5. ✅ 提升团队协作效率

---

**创建日期**: 2025-12-12  
**维护团队**: TaPro Team  
**版本**: 1.0.0





