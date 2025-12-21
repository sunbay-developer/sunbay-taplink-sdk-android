# AmountInfo 使用示例

## 概述

AmountInfo 类用于描述交易中的详细金额信息，包括订单金额、小费、税额、附加费、现金返还和服务费等。

## 新增字段

### serviceFee - 服务费

新增了 `serviceFee` 字段，用于表示交易中的服务费金额。

```kotlin
val serviceFee: BigDecimal? = null  // 服务费（可选，单位：基本货币单位）
```

## 使用示例

### 1. 基本使用

```kotlin
import java.math.BigDecimal

// 创建包含服务费的金额信息
val amountInfo = AmountInfo(
    orderAmount = BigDecimal("100.00"),        // 订单金额 $100.00
    pricingCurrency = "USD",                   // 美元
    tipAmount = BigDecimal("15.00"),           // 小费 $15.00
    taxAmount = BigDecimal("8.50"),            // 税额 $8.50
    surchargeAmount = BigDecimal("2.00"),      // 附加费 $2.00
    cashbackAmount = BigDecimal("5.00"),       // 现金返还 $5.00
    serviceFee = BigDecimal("3.50")            // 服务费 $3.50
)
```

### 2. 链式调用

```kotlin
// 使用链式调用设置金额信息
val amountInfo = AmountInfo(
    orderAmount = BigDecimal("100.00"),
    pricingCurrency = "USD"
)
.setTipAmount(BigDecimal("15.00"))
.setTaxAmount(BigDecimal("8.50"))
.setSurchargeAmount(BigDecimal("2.00"))
.setCashbackAmount(BigDecimal("5.00"))
.setServiceFee(BigDecimal("3.50"))  // 新增的服务费设置方法
```

### 3. 在 PaymentRequest 中使用

```kotlin
// 创建支付请求
val paymentRequest = PaymentRequest(
    action = "SALE",
    referenceOrderId = "ORDER_001",
    transactionRequestId = "TXN_001",
    description = "商品购买",
    amount = AmountInfo(
        orderAmount = BigDecimal("100.00"),
        pricingCurrency = "USD",
        serviceFee = BigDecimal("3.50")  // 包含服务费
    )
)
```

### 4. 动态计算总金额

```kotlin
fun calculateTotalAmount(amountInfo: AmountInfo): BigDecimal {
    var total = amountInfo.orderAmount
    
    // 添加可选费用
    amountInfo.tipAmount?.let { total = total.add(it) }
    amountInfo.taxAmount?.let { total = total.add(it) }
    amountInfo.surchargeAmount?.let { total = total.add(it) }
    amountInfo.serviceFee?.let { total = total.add(it) }  // 包含服务费
    
    // 减去现金返还
    amountInfo.cashbackAmount?.let { total = total.subtract(it) }
    
    return total
}

// 使用示例
val amountInfo = AmountInfo(
    orderAmount = BigDecimal("100.00"),
    pricingCurrency = "USD",
    serviceFee = BigDecimal("3.50")
)

val totalAmount = calculateTotalAmount(amountInfo)
println("总金额: $totalAmount")  // 输出: 总金额: 103.50
```

### 5. Java 调用示例

```java
// Java 中使用 AmountInfo
AmountInfo amountInfo = new AmountInfo(
    new BigDecimal("100.00"),  // orderAmount
    "USD",                     // pricingCurrency
    new BigDecimal("15.00"),   // tipAmount
    new BigDecimal("8.50"),    // taxAmount
    new BigDecimal("2.00"),    // surchargeAmount
    new BigDecimal("5.00"),    // cashbackAmount
    new BigDecimal("3.50")     // serviceFee
);

// 或者使用链式调用
AmountInfo amountInfo2 = new AmountInfo(
    new BigDecimal("100.00"),
    "USD"
).setServiceFee(new BigDecimal("3.50"));
```

## 字段说明

| 字段名 | 类型 | 必需 | 说明 |
|--------|------|------|------|
| orderAmount | BigDecimal | 是 | 订单金额 |
| pricingCurrency | String | 是 | 定价货币（ISO 4217标准） |
| tipAmount | BigDecimal? | 否 | 小费金额 |
| taxAmount | BigDecimal? | 否 | 税额 |
| surchargeAmount | BigDecimal? | 否 | 附加费金额 |
| cashbackAmount | BigDecimal? | 否 | 现金返还金额 |
| **serviceFee** | **BigDecimal?** | **否** | **服务费（新增）** |

## 链式调用方法

| 方法名 | 参数 | 返回值 | 说明 |
|--------|------|--------|------|
| setOrderAmount() | BigDecimal | AmountInfo | 设置订单金额 |
| setPricingCurrency() | String | AmountInfo | 设置定价货币 |
| setTipAmount() | BigDecimal | AmountInfo | 设置小费金额 |
| setTaxAmount() | BigDecimal | AmountInfo | 设置税额 |
| setSurchargeAmount() | BigDecimal | AmountInfo | 设置附加费金额 |
| setCashbackAmount() | BigDecimal | AmountInfo | 设置现金返还金额 |
| **setServiceFee()** | **BigDecimal** | **AmountInfo** | **设置服务费（新增）** |

## 使用场景

### 1. 餐饮行业
```kotlin
// 餐厅订单：包含服务费
val restaurantAmount = AmountInfo(
    orderAmount = BigDecimal("50.00"),    // 餐费
    pricingCurrency = "USD",
    tipAmount = BigDecimal("10.00"),      // 小费
    taxAmount = BigDecimal("4.25"),       // 税费
    serviceFee = BigDecimal("2.50")       // 服务费
)
```

### 2. 在线服务
```kotlin
// 在线服务：平台服务费
val onlineServiceAmount = AmountInfo(
    orderAmount = BigDecimal("200.00"),   // 服务费用
    pricingCurrency = "USD",
    serviceFee = BigDecimal("10.00")      // 平台服务费
)
```

### 3. 酒店预订
```kotlin
// 酒店预订：包含服务费和税费
val hotelAmount = AmountInfo(
    orderAmount = BigDecimal("300.00"),   // 房费
    pricingCurrency = "USD",
    taxAmount = BigDecimal("25.50"),      // 税费
    serviceFee = BigDecimal("15.00")      // 酒店服务费
)
```

## 注意事项

1. **货币单位**: 所有金额字段都使用相同的基本货币单位
2. **精度**: 使用 BigDecimal 确保金额计算的精度
3. **可选字段**: serviceFee 是可选字段，可以为 null
4. **链式调用**: 支持链式调用，方便构建复杂的金额信息
5. **向后兼容**: 新增字段不影响现有代码的使用

## 迁移指南

如果你的代码中已经使用了 AmountInfo，添加 serviceFee 字段不会影响现有功能：

```kotlin
// 现有代码继续工作
val existingAmount = AmountInfo(
    orderAmount = BigDecimal("100.00"),
    pricingCurrency = "USD"
)

// 新代码可以使用服务费
val newAmount = existingAmount.setServiceFee(BigDecimal("5.00"))
```