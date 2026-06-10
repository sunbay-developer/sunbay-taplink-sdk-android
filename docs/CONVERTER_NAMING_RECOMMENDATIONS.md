# 转换器类命名建议

## 当前命名问题分析

现有的转换器类名不够明确，无法清晰表达其具体职责：

### 当前命名
- `RequestConverter` - 名称过于泛泛，看不出具体转换什么
- `TransactionRequestConverter` - 名称冗长，职责不够明确

## 转换器职责分析

### RequestConverter 的实际职责
- 将任何请求对象转换为 `BasicRequest`（底层传输协议格式）
- 处理签名生成、时间戳、traceId 等协议层字段
- 负责协议层面的数据封装和安全处理

### TransactionRequestConverter 的实际职责  
- 将具体的交易请求类型（SaleRequest, AuthRequest, RefundRequest等）转换为统一的 `PaymentRequest`
- 统一不同交易类型的请求格式，简化上层处理逻辑
- 负责业务层面的数据转换和字段映射

## 推荐的新命名

### 方案一：基于协议层次命名

1. **`RequestConverter`** → **`ProtocolRequestConverter`**
   - 明确表示这是协议层的请求转换器
   - 负责转换为底层传输协议格式

2. **`TransactionRequestConverter`** → **`BusinessRequestConverter`**
   - 表示这是业务层的请求转换器
   - 负责统一业务请求格式

### 方案二：基于目标格式命名

1. **`RequestConverter`** → **`BasicRequestConverter`**
   - 明确表示转换目标是 `BasicRequest`
   - 直观表达转换结果

2. **`TransactionRequestConverter`** → **`PaymentRequestConverter`**
   - 明确表示转换目标是 `PaymentRequest`
   - 直观表达转换结果

### 方案四：基于具体功能命名（推荐）

1. **`RequestConverter`** → **`ProtocolRequestBuilder`**
   - 表示这是协议请求的构建器
   - 强调构建完整协议请求的功能
   - 避免与基础类混淆

2. **`TransactionRequestConverter`** → **`PaymentRequestAdapter`**
   - 表示这是支付请求的适配器
   - 强调适配不同交易类型到统一格式的功能
   - 符合适配器模式的命名惯例

### 方案五：基于处理层次命名

1. **`RequestConverter`** → **`WireFormatConverter`**
   - 表示转换为网络传输格式
   - 明确表达底层协议转换的职责

2. **`TransactionRequestConverter`** → **`BusinessRequestNormalizer`**
   - 表示业务请求的标准化器
   - 强调统一业务请求格式的功能

## 推荐方案

**推荐使用方案四：基于具体功能命名（新增）**

### 理由：
1. **功能明确**: 从类名就能看出具体的转换功能
2. **避免歧义**: 不会让人误解为基础类或父类
3. **职责清晰**: 明确表达每个转换器的具体职责
4. **语义准确**: 准确反映转换器的实际作用

### 具体命名：
- `RequestConverter` → **`ProtocolRequestBuilder`**
- `TransactionRequestConverter` → **`PaymentRequestAdapter`**

## 新命名的优势

### ProtocolRequestBuilder
- ✅ 明确表示构建协议层请求
- ✅ 突出构建完整传输协议的职责
- ✅ 避免与基础类或父类混淆
- ✅ 语义准确，表达构建功能

### PaymentRequestAdapter  
- ✅ 明确表示适配为支付请求格式
- ✅ 突出适配不同交易类型的职责
- ✅ 符合适配器模式的命名惯例
- ✅ 与业务领域直接对应

## 实施建议

### 重命名步骤
1. 重命名类文件
2. 更新类名和注释
3. 更新所有引用
4. 更新相关文档

### 影响范围
- 需要更新引用这两个转换器的所有文件
- 主要影响 Manager 类中的导入和调用
- 需要更新相关的文档和注释

## 总结

通过更准确的命名，可以：
- 提高代码的可读性和可维护性
- 让开发者快速理解转换器的具体职责
- 减少代码理解的认知负担
- 符合良好的软件工程实践