# MirCode (移动端 Git 审查与工作区管理平台)

<p align="center">
  <img src="android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml" width="96" height="96" alt="MirCode Logo" />
</p>

<p align="center">
  <b>遵循 Material Design 3 (MD3) 规范的移动端代码审查、全功能 Git 协作与远程工作区管理套件</b><br>
  随时随地连接您的电脑（Arch Linux / macOS / Windows），尽享无缝的移动端代码审查体验。
</p>

<p align="center">
  <a href="https://github.com/LaT-SKY/MirCode/releases"><img src="https://img.shields.io/github/v/release/LaT-SKY/MirCode?color=6750A4&label=Release" alt="Release"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B%20(API%2026%2B)-3DDC84?logo=android&logoColor=white" alt="Android">
  <img src="https://img.shields.io/badge/Arch%20Linux-makepkg%20ready-1793D1?logo=arch-linux&logoColor=white" alt="Arch Linux">
  <img src="https://img.shields.io/badge/Material%20Design%203-Jetpack%20Compose-4285F4?logo=google&logoColor=white" alt="MD3">
  <img src="https://img.shields.io/badge/Companion-Go%201.23%2B-00ADD8?logo=go&logoColor=white" alt="Go">
  <img src="https://img.shields.io/badge/License-MIT-green.svg" alt="License">
</p>

---

## 🌟 核心特性 (Key Features)

1. **🎨 纯正 Material Design 3 (Material You)**
   - 采用 Jetpack Compose 打造，原生支持 Material You 动态色彩取色（基于系统壁纸与主题）。
   - 完备的自适应深色/浅色模式、流畅手势动效与标准非对称圆角质感。

2. **📁 远程文件系统浏览与工作区保存**
   - 手机连接电脑后，可直接在手机上浏览电脑硬盘目录，并一键设为当前工作区。
   - 电脑端亦支持运行 `mircode .` 或 `mircode /path/to/project`，启动即自动挂载指定项目。

3. **🔍 差异查看 (Git Diff) 与闭环审查 (Code Review)**
   - **高亮差异查看器**：语法高亮与行号对照，清晰呈现增删代码块。
   - **闭环审查失效机制**：文件一旦标记为“已审查”，系统会严格追踪其 Git Blob SHA / SHA256 哈希。一旦该文件在电脑或手机上发生后续修改，已审查状态**毫秒级自动重置为未审查**，并在手机端高亮提示“审查后被改动”。

4. **⚡ 全能专业 Git 工具箱**
   - **工作区状态 (Status)**：清晰呈现暂存区、未暂存、未跟踪与冲突状态。
   - **提交管理 (Commit)**：支持编写 Commit Message、一键 Amend 上次提交。
   - **分支流转 (Branches)**：分支列表、检出 (Checkout)、新建分支与删除分支。
   - **远程同步 (Sync)**：一键执行 Fetch、Pull、Push。
   - **储藏管理 (Stash)**：支持 Stash 保存、Stash Pop 恢复。
   - **可视化冲突解决 (Conflict Resolution)**：一键采纳本地 (Ours)、采纳传入 (Theirs) 或进入内置代码编辑器手动合并。

5. **✍️ 内置代码编辑器 (Native Code Editor)**
   - 原生高性能文本编辑器，带行号指示与代码折叠。
   - 专属虚拟符号工具栏（`Tab`, `{`, `}`, `(`, `)`, `;`, `=`, `->` 等），移动端微调代码快如闪电。
   - 支持直接从 Diff 页面一键跳转至编辑器，修改保存后实时通过 WebSocket 双向流同步回电脑磁盘。

6. **🌐 跨网络自由连接 (LAN & Tailscale)**
   - 支持同一局域网 Wi-Fi 直连（控制台自动生成终端彩色配对二维码，扫码即连）。
   - 完美适配 **Tailscale / WireGuard** 虚拟内网，外出通勤时也能随时穿透回办公室开发机进行代码审查。

---

## 📐 系统架构 (Architecture)

```
┌──────────────────────────────────────┐       WebSocket / HTTP        ┌──────────────────────────────────────┐
│       开发机 (PC / Arch Linux)        │  ◄────────────────────────►  │          移动端 (Android App)         │
│                                      │                               │                                      │
│  ┌────────────────────────────────┐  │                               │  ┌────────────────────────────────┐  │
│  │   mircode Companion 守护服务    │  │  • 局域网 Wi-Fi / 终端二维码   │  │   Material Design 3 Compose    │  │
│  │   (Go 单一静态二进制可执行文件) │  │  • Tailscale 跨公网安全加密通道│  │   (动态色彩 / 响应式布局)       │  │
│  └───────────────┬────────────────┘  │                               │  └────────────────┬───────────────┘  │
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

#### 方案 B：直接运行独立二进制或 Go 编译
```bash
# 克隆仓库
git clone https://github.com/LaT-SKY/MirCode.git
cd MirCode/companion

# 编译生成单文件
go build -o mircode ./cmd/mircode

# 启动服务并指定当前项目为工作区
./mircode .
```

启动后，终端将输出配对二维码与连接信息：
```text
==================================================================
   MirCode Companion Service  v1.0.0
   Material Design 3 移动端代码审查与工作区伴侣服务
==================================================================
📂 当前工作区: /home/neo/Code/Projects/MirCode
🔑 安全配对令牌 (Token): 4b9e28f1a07c3d5e
------------------------------------------------------------------
🌐 可用连接地址:
   - Tailscale 跨网直连: http://100.64.12.34:18980
   - 局域网 Wi-Fi 直连 : http://192.168.1.108:18980
   - 本机测试连接     : http://127.0.0.1:18980
------------------------------------------------------------------
📱 安卓端扫码配对二维码 (在 MirCode App 扫码即连):
[  TERMINAL QR CODE RENDERED HERE  ]
==================================================================
```

---

### 2. 移动端安装与配对 (Android)

1. 从 [GitHub Releases](https://github.com/LaT-SKY/MirCode/releases) 下载最新的 `MirCode-v1.0.0.apk` 并安装至手机。
2. 打开 MirCode 应用：
   - **扫码配对**：点击扫描终端中打印的配对二维码，一键自动填入 IP、端口与 Token。
   - **手动输入**：填入电脑的局域网 IP（如 `192.168.1.108`）或 Tailscale IP（如 `100.64.12.34`），端口 `18980`，以及安全令牌。
3. 点击 **立即连接工作区**，即可进入项目主控台！

---

## 📖 核心功能操作指南 (User Manual)

### 1. 切换与选择计算机文件夹
- 点击右上角的 **切换工作区** 图标，打开远程文件系统浏览器。
- 目录列表中自动识别并标注包含 `.git` 的仓库项目。
- 导航至任意项目目录后，点击底部 **“设为工作区”**，伴侣服务将立即切换监听与 Git 上下文。

### 2. 代码审查与自动失效流转
- 在 **“审查 & 变更”** 标签页中，顶部直观呈现当前的 **审查进度条**（如 `4 / 7 文件已审查`）。
- 点击任意文件进入 **Diff 差异页面**，阅读改动后点击悬浮按钮 **“标记为已审查”**。
- **自动失效验证**：当您或团队成员在电脑端通过 VS Code / Vim 保存了该文件的新修改，MirCode 伴侣程序将立即检测到文件内容 Hash 变动，**自动将审查状态置为未审查**，并在手机端弹出提示并标注 **“审查后被改动”** 警告胶囊。

### 3. Git 暂存、提交与分支操作
- **暂存/取消暂存**：点击文件条目右侧的 `+` 或 `-` 图标，或者点击分组头部的 “全部暂存” / “取消全暂存”。
- **提交代码**：点击右下角 **“提交”** FAB 唤出 MD3 底部抽屉，输入提交信息，可勾选 `Amend (合并到上次提交)` 后一键完成提交。
- **分支管理**：在 **“分支 & 工具”** 标签页中，可随时一键新建分支、检出切换本地/远程分支。
- **推送与拉取**：顶部快捷面板提供 Fetch、Pull、Push 快速触发。
- **冲突解决**：当发生 Merge / Rebase 冲突时，冲突解决卡片将提供 `采纳本地 (Ours)`、`采纳传入 (Theirs)` 以及 `编辑器合并` 三种一键处理选项。

### 4. 代码微调与编辑
- 点击文件条目上的 ✏️ **编辑图标** 打开内置全屏代码编辑器。
- 底部常驻虚拟符号栏，输入括号、分号、制表符极为便捷。
- 点击右上角 **保存** 按钮，修改内容将毫秒级写回电脑端，且自动触发 Git 状态刷新。

---

## 🛠️ 本地构建与开发指南 (Build from Source)

### 编译 Companion 守护服务
```bash
cd companion
go test ./...
go build -o mircode ./cmd/mircode
```

### 打包 Arch Linux 软件包
```bash
cd archlinux
makepkg -f --nodeps
# 产出 mircode-1.0.0-1-x86_64.pkg.tar.zst
```

### 编译 Android 应用
```bash
cd android
./gradlew assembleRelease
# 产出 android/app/build/outputs/apk/release/app-release.apk
```

---

## 📄 开源许可 (License)

本项目采用 [MIT License](LICENSE) 开源。欢迎提交 Issue 与 Pull Request 共同完善 MirCode！
