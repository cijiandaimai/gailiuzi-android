# 09 初版 Android 应用实现说明

## 1. 版本信息

- 产品名：改流子
- 版本：`0.2.0`
- 包名：`com.shangbaobao.app`
- 最低 API：31
- Target/Compile API：36
- 技术：Kotlin 2.3.21、AGP 8.13.2、Compose BOM 2025.08.01

## 2. 当前完成范围

### 工作台

- 展示 Agent 运行状态。
- 一键进入无障碍设置。
- 启动和停止前台辅助服务。
- 抖音、美团、小红书平台标识。
- 前台、逛街、公关模式配置。
- 明确展示初版不自动发布评论。

### 设备动态

- 显示辅助服务启动和停止事件。
- 显示受支持平台的页面切换事件。
- 显示受支持平台的通知发现事件。
- 通知正文不写入动态日志。
- 支持清空本次进程中的动态。

### 设置

- 无障碍操作辅助状态与跳转。
- 通知监听状态与跳转。
- Android 通知权限申请。
- L0、L1、L2 自动化等级。
- 本地保存模式和等级配置。

### AI 配置

- OpenAI GPT 与豆包方舟双模型配置、主模型选择和失败回退。
- API Key 使用 Android Keystore 加密后保存。
- 支持连接测试与独立的回复生成测试台。
- 支持身份、性格、语气、边界和五种预设人格。
- 支持预置话术库开关、意图关键词匹配、优先级及增删改。
- 预留抖音、美团、小红书官方接口配置，用于平台授权后的辅助任务。

### 风控核心

- 潜在客户七维评分。
- 高、中、低潜力分层。
- P0/P1 强制升级。
- 缺少作品和对话上下文时阻止写操作。
- 小红书默认草稿审批。
- 仅自有、低风险、官方写能力场景允许进入确定性动作。

## 3. 代码结构

```text
app/src/main/java/com/shangbaobao/app
├── MainActivity.kt
├── agent
│   ├── AgentEventStore.kt
│   ├── AgentForegroundService.kt
│   ├── ShangBaoAccessibilityService.kt
│   └── ShangBaoNotificationListener.kt
├── core
│   ├── InteractionPolicy.kt
│   └── ProspectScorer.kt
├── data
│   └── ModeSettingsRepository.kt
├── model
│   └── AppModels.kt
├── platform
│   └── PermissionInspector.kt
└── ui
    ├── ShangBaoBaoApp.kt
    └── theme
```

## 4. 无障碍服务当前行为

当前只监听以下包的页面状态和内容变化：

- `com.ss.android.ugc.aweme`
- `com.sankuai.meituan`
- `com.dianping.v1`
- `com.xingin.xhs`

服务只记录平台和页面类名，不读取、保存或发送评论正文，也没有执行点击和文本输入。

## 5. 通知监听当前行为

只识别上述平台的通知来源，动态日志中记录“发现平台通知”，不保存通知标题、正文、用户名称或其他敏感内容。

## 6. 测试

已加入：

- 高意图本地候选评分测试。
- 风险扣分测试。
- P1 内容强制升级测试。
- 小红书人工审批测试。
- 缺少上下文阻断测试。
- 话术意图匹配、禁用过滤和提示词组装测试。

构建命令：

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

本次验证结果：

- Debug APK 构建成功。
- JVM 单元测试 11 项通过，失败 0 项。
- Android Lint 错误 0 项。
- APK 路径：`app/build/outputs/apk/debug/app-debug.apk`。

## 7. 下一阶段

1. 增加本地任务数据库和任务状态机。
2. 实现 Accessibility 页面快照与脱敏节点树。
3. 完成抖音开放平台 OAuth 和自有评论 API。
4. 建立美团经营宝页面指纹和门店校验。
5. 建立小红书“读取—生成草稿—人工确认”流程。
6. 接入后端 API、知识库和模型网关。
7. 增加截图授权、OCR 和错误恢复。
8. 增加真机自动化测试和平台版本灰度机制。
