# MirCode (移动端 Git 审查与工作区管理平台)

<p align="center">
  <img src="android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml" width="96" height="96" alt="MirCode Logo" />
</p>

<p align="center">
  <b>遵循 Material Design 3 (MD3) 规范的移动端代码审查、全功能 Git 协作与远程工作区管理套件</b><br>
  随时随地连接您的电脑（Arch Linux / macOS / Windows），免配置异地打通工作区，尽享沉浸式移动端代码审查体验。
</p>

<p align="center">
  <a href="https://github.com/LaT-SKY/MirCode/releases"><img src="https://img.shields.io/github/v/release/LaT-SKY/MirCode?color=6750A4&label=Release" alt="Release"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B%20(API%2026%2B)-3DDC84?logo=android&logoColor=white" alt="Android">
  <img src="https://img.shields.io/badge/Arch%20Linux-makepkg%20ready-1793D1?logo=arch-linux&logoColor=white" alt="Arch Linux">
  <img src="https://img.shields.io/badge/Material%20Design%203-Jetpack%20Compose-4285F4?logo=google&logoColor=white" alt="MD3">
  <img src="https://img.shields.io/badge/Companion-Go%201.23%2B-00ADD8?logo=go&logoColor=white" alt="Go">
  <img src="https://img.shields.io/badge/TailCat-P2P%20Ready-blueviolet" alt="TailCat">
  <img src="https://img.shields.io/badge/License-MIT-green.svg" alt="License">
</p>

---

## 🌟 核心特性 (Key Features)

1. **🚀 异地免配置连接 (TailCat P2P 隧道 + 6 位配对码)**
   - **完全零配置**：电脑端启动 `mircode .` 自动生成 **6 位动态配对码**（如 `839-102`）与扫码二维码。
   - **无需同一 Wi-Fi**：手机端在外面使用 4G/5G 移动蜂窝网络，只需输入 6 位配对码或扫码，无需公网 IP、无需端口映射、无需注册任何账号，一秒穿透直通！
   - **TailCat P2P 加速**：底层优先尝试 UDP P2P 打洞直连，穿透失败自动平滑回退至公共/私有加密中继。

2. **🎨 纯正 Material Design 3 (Material You)**
   - 采用 Jetpack Compose 打造，原生支持 Material You 动态色彩取色（基于系统壁纸与主题自适应色调）。
   - 完备的自适应深色/浅色模式、流畅手势动效与标准非对称圆角质感。

3. **📁 远程文件系统浏览与工作区保存**
   - 手机连接电脑后，可直接在手机上浏览电脑硬盘目录，并一键设为当前工作区。
   - 电脑端亦支持运行 `mircode .` 或 `mircode /path/to/project`，启动即自动挂载指定项目。

4. **🔍 差异查看 (Git Diff) 与闭环审查 (Code Review)**
   - **高亮差异查看器**：语法高亮与行号对照，清晰呈现增删代码块。
   - **闭环审查失效机制**：文件一旦标记为“已审查”，系统会严格追踪其 Git Blob SHA / SHA256 哈希。一旦该文件在电脑或手机上发生后续修改，已审查状态**毫秒级自动重置为未审查**，并在手机端高亮提示“审查后被改动”。

5. **⚡ 全能专业 Git 工具箱**
   - **工作区状态 (Status)**：清晰呈现暂存区、未暂存、未跟踪与冲突状态。
   - **提交管理 (Commit)**：支持编写 Commit Message、一键 Amend 上次提交。
   - **分支流转 (Branches)**：分支列表、检出 (Checkout)、新建分支与删除分支。
   - **远程同步 (Sync)**：一键执行 Fetch、Pull、Push。
   - **储藏管理 (Stash)**：支持 Stash 保存、Stash Pop 恢复。
   - **可视化冲突解决 (Conflict Resolution)**：一键采纳本地 (Ours)、采纳传入 (Theirs) 或进入内置代码编辑器手动合并。

6. **✍️ 内置代码编辑器 (Native Code Editor)**
   - 原生高性能文本编辑器，带行号指示与代码折叠。
   - 专属虚拟符号工具栏（`Tab`, `{`, `}`, `(`, `)`, `;`, `=`, `->` 等），移动端微调代码快如闪电。
   - 支持直接从 Diff 页面一键跳转至编辑器，修改保存后实时通过 WebSocket 双向流同步回电脑磁盘。

---

## 📐 系统拓扑与双模穿透架构

```
┌──────────────────────────────────────┐       WebSocket / TailCat     ┌──────────────────────────────────────┐
│       开发机 (PC / Arch Linux)        │  ◄────────────────────────►  │          移动端 (Android App)         │
│                                      │                               │                                      │
│  ┌────────────────────────────────┐  │  • 方式 1：6 位数字配对码直连  │  ┌────────────────────────────────┐  │
│  │   mircode Companion 守护服务    │  │  • 方式 2：TailCat P2P 用户态 │  │   Material Design 3 Compose    │  │
│  │   (Go 单一静态二进制可执行文件) │  │  • 方式 3：终端二维码扫码即连 │  │   (动态色彩 / 响应式布局)       │  │
│  └───────────────┬────────────────┘  │  • 方式 4：局域网同一 Wi-Fi 直连│  └────────────────┬───────────────┘  │
│                  │                   │                               │                   │                  │
│       ┌──────────┴──────────┐        │                               │       ┌───────────┴──────────┐       │
│       ▼                     ▼        │                               │       ▼                      ▼       │
│ ┌───────────┐         ┌────────────┐ │                               │ ┌───────────┐          ┌───────────┐ │
│ │  fsnotify │         │ Git CLI    │ │                               │ │ Diff 查看 │          │ 代码编辑  │ │
│ │  文件监听 │         │ 全能执行器 │ │                               │ │ & 审查机  │          │ & 冲突解决│ │
│ └─────┬─────┘         └────────────┘ │                               │ └───────────┘          └───────────┘ │
│       ▼                              │                               └──────────────────────────────────────┘
│ ┌──────────────────────────────────┐ │
│ │ 审查状态机 (.mircode/review.json)│ │
│ │ (改动自动检测 & 秒级推送失效)    │ │
│ └──────────────────────────────────┘ │
└──────────────────────────────────────┘
```

---

## 🚀 快速上手 (Getting Started)

### 1. 电脑端部署 (Arch Linux / PC)

#### 方案 A：通过 Arch Linux 软件包安装 (推荐)
从 [GitHub Releases](https://github.com/LaT-SKY/MirCode/releases) 下载最新的 Arch Linux 安装包并安装：
```bash
# 安装软件包
sudo pacman -U mircode-1.0.0-1-x86_64.pkg.tar.zst

# (可选) 开启并启动 systemd 用户守护服务
systemctl --user enable --now mircode
```

#### 方案 B：直接运行独立二进制
```bash
# 下载并解压 mircode 二进制后，在项目目录执行：
./mircode .
```

启动后控制台将打印：
```text
==================================================================
   MirCode Companion Service  v1.0.0
   Material Design 3 移动端代码审查与工作区伴侣服务
==================================================================
📂 当前工作区: /home/neo/Code/Projects/MirCode
🔑 安全配对令牌 (Token): 4b9e28f1a07c3d5e
------------------------------------------------------------------
🚀 异地免配置连接 (无需同一 Wi-Fi，随时随地连接):
   🔢 6 位专属配对码 : 【 839 - 102 】(手机 App 输入即连)
   🐱 TailCat P2P 隧道: 备用中继已就绪 (安装 tailcat CLI 可加速 P2P 直连)
------------------------------------------------------------------
🌐 局域网直连地址 (同一 Wi-Fi):
   - Wi-Fi 内网地址   : http://192.168.1.108:18980
------------------------------------------------------------------
📱 安卓端扫码配对二维码 (在 MirCode App 扫码即连):
[  TERMINAL QR CODE RENDERED HERE  ]
==================================================================
```

---

### 2. 移动端安装与连接 (Android)

1. 从 [GitHub Releases](https://github.com/LaT-SKY/MirCode/releases) 下载 `MirCode-v1.0.0.apk` 安装。
2. 打开 MirCode：
   - **方式一（推荐·异地直连）**：在首页直接输入终端显示的 **6 位配对码**（如 `839-102`），点击“一键免配置异地连接”，立即穿透连通！
   - **方式二（扫码直连）**：点击扫码识别电脑终端的二维码，一秒完成全部配对。
   - **方式三（局域网直连）**：切换到“局域网直连”标签页，输入内网 IP 进行连接。

---

### 3. (可选) 私有中继服务部署 (`mircode-relay`)

若您希望完全搭建自己的私有中继中转节点，可在 VPS 上执行：
```bash
cd relay
go build -o mircode-relay ./cmd/mircode-relay
./mircode-relay --port 18990
```
并在电脑启动时指定中继：`mircode --relay http://your-vps-ip:18990 .`

---

## 📄 开源许可 (License)

本项目采用 [MIT License](LICENSE) 开源。欢迎提交 Issue 与 Pull Request 共同完善 MirCode！
