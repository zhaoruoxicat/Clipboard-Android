# Clipboard Sync - Android App

## 📌 项目简介

这是一个配套 **PHP 剪切板同步工具 API** 使用的 Android 客户端应用，实现多设备之间的剪切板内容同步。

项目地址：[GitHub - zhaoruoxicat/PHPclipboard: 一个PHP多设备云剪切板同步工具 · GitHub](https://github.com/zhaoruoxicat/PHPclipboard)

通过本应用，你可以在不同设备（如电脑 ↔ 手机）之间快速共享文本或图片内容，无需手动复制粘贴或借助第三方工具。

---

## 🚀 核心功能

### 🔄 剪切板同步

- 获取本机剪切板内容
- 上传至 PHP API 服务端
- 支持多设备同步访问

### 📥 拉取远程剪切板

- 从服务器获取最新剪切板内容
- 一键复制到本地剪切板

### 🖼 图片剪切板支持

- 支持图片转 Base64 同步
- 支持从云剪切板读取图片写入安卓剪切板同时保存到相册

### 🌐 API 对接

- 自定义 API 地址
- 支持 Token 鉴权
- 兼容自建 PHP 服务端

<img width="1080" height="2400" alt="IMG_20260426_150831" src="https://github.com/user-attachments/assets/dd5c37a9-7463-4447-a8b7-3af287b4d851" />
<img width="1080" height="2400" alt="IMG_20260426_150812" src="https://github.com/user-attachments/assets/05439e76-2455-43b8-80dd-3e76aa13da5f" />

---

## 🧩 技术特点

- 基于 Android 原生开发
- 使用系统剪切板监听机制
- HTTP API 通信（兼容 PHP 后端）
- 支持 Base64 图片编码传输
- 轻量级设计，无复杂依赖

---

## 📦 使用方式

1. 部署 PHP 剪切板同步 API（服务端）
2. 在 App 中填写：
   - API 地址
   - Token（如有）
3. 启动应用并授权剪切板访问
4. 开始同步

---

## 🔐 安全说明

- 建议通过 HTTPS 使用 API
- 建议设置 Token 防止未授权访问
- 不建议在公网开放未鉴权接口

---

## 🙌 说明

本项目为配合 PHP 后端 API 使用的轻量级工具，适合个人跨设备剪切板同步场景。
