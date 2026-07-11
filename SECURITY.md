# 安全政策

## 报告范围

请优先报告以下问题：凭据泄露、跨租户访问、未授权平台操作、合规规则绕过、知识库数据泄露和网关请求伪造。

## 报告方式

请通过私有仓库的 Security Advisory 提交，附上受影响版本、复现步骤和建议修复方案。不要在公开 Issue 中张贴 API Key、Token、商户数据或用户评论原文。

## 凭据处理

- 仓库禁止提交 API Key、Client Secret、网关 Token、签名密钥和 `local.properties`。
- 手机端敏感配置必须使用 Android Keystore 保护。
- 生产环境优先使用短期业务令牌访问后端模型网关，不向客户端下发厂商主 Key。
