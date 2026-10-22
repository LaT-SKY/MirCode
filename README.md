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
  <a href="https://aur.archlinux.org/packages/mircode-bin"><img src="https://img.shields.io/aur/version/mircode-bin?logo=arch-linux&color=1793D1&label=AUR" alt="AUR"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B%20(API%2026%2B)-3DDC84?logo=android&logoColor=white" alt="Android">
  <img src="https://img.shields.io/badge/Arch%20Linux-makepkg%20ready-1793D1?logo=arch-linux&logoColor=white" alt="Arch Linux">
  <img src="https://img.shields.io/badge/Material%20Design%203-Jetpack%20Compose-4285F4?logo=google&logoColor=white" alt="MD3">
  <img src="https://img.shields.io/badge/Companion-Go%201.23%2B-00ADD8?logo=go&logoColor=white" alt="Go">
  <img src="https://img.shields.io/badge/TailCat-P2P%20Ready-blueviolet" alt="TailCat">
  <img src="https://img.shields.io/badge/TUI-Config%20Manager-ff69b4" alt="TUI">
  <img src="https://img.shields.io/badge/License-MIT-green.svg" alt="License">
</p>

---

## 🌟 核心特性 (Key Features)

1. **🖥️ 桌面终端配置中心 (TUI Settings Manager)**
   - 运行 **`mircode config`** 或 **`mircode tui`** 即可呼出全屏交互式终端配置面板（基于 Charm Bubbletea + Lipgloss 打造）。
   - **一键开机自启**：支持在 TUI 中一键开关开机自启（Linux 自动生成并托管 systemd user 服务；Windows 自动托管启动项注册表与自启目录）。
   - **服务状态控制**：在终端按 `s` 启动/停止后台伴侣服务，按 `r` 重启服务。
   - **参数配置**：可视化修改监听端口、重新生成安全令牌、设置默认工作区及私有中继节点。

2. **🚀 异地免配置连接 (TailCat P2P 隧道 + 6 位配对码)**
   - **完全零配置**：电脑端启动 `mircode .` 自动生成 **6 位动态配对码**（如 `839-102`）与扫码二维码。
   - **无需同一 Wi-Fi**：手机端在外面使用 4G/5G 移动蜂窝网络，只需输入 6 位配对码或扫码，无需公网 IP、无需端口映射、无需注册任何账号，一秒穿透直通！
   - **TailCat P2P 加速**：底层优先尝试 UDP P2P 打洞直连，穿透失败自动平滑回退至公共/私有加密中继。

3. **🎨 纯正 Material Design 3 (Material You)**
   - 采用 Jetpack Compose 打造，原生支持 Material You 动态色彩取色（基于系统壁纸与主题自适应色调）。
   - 完备的自适应深色/浅色模式、流畅手势动效与标准非对称圆角质感。

4. **📁 远程文件系统浏览与工作区保存**
   - 手机连接电脑后，可直接在手机上浏览电脑硬盘目录，并一键设为当前工作区。
   - 电脑端亦支持运行 `mircode .` 或 `mircode /path/to/project`，启动即自动挂载指定项目。

5. **🔍 差异查看 (Git Diff) 与闭环审查 (Code Review)**
   - **高亮差异查看器**：语法高亮与行号对照，清晰呈现增删代码块。
   - **闭环审查失效机制**：文件一旦标记为“已审查”，系统会严格追踪其 Git Blob SHA / SHA256 哈希。一旦该文件在电脑或手机上发生后续修改，已审查状态**毫秒级自动重置为未审查**，并在手机端高亮提示“审查后被改动”。

6. **⚡ 全能专业 Git 工具箱**
   - **工作区状态 (Status)**：清晰呈现暂存区、未暂存、未跟踪与冲突状态。
   - **提交管理 (Commit)**：支持编写 Commit Message、一键 Amend 上次提交。
   - **分支流转 (Branches)**：分支列表、检出 (Checkout)、新建分支与删除分支。
   - **远程同步 (Sync)**：一键执行 Fetch、Pull、Push。
   - **储藏管理 (Stash)**：支持 Stash 保存、Stash Pop 恢复。
   - **可视化冲突解决 (Conflict Resolution)**：一键采纳本地 (Ours)、采纳传入 (Theirs) 或进入内置代码编辑器手动合并。

7. **✍️ 内置代码编辑器 (Native Code Editor)**
   - 原生高性能文本编辑器，带行号指示与代码折叠。
   - 专属虚拟符号工具栏（`Tab`, `{`, `}`, `(`, `)`, `;`, `=`, `->` 等），移动端微调代码快如闪电。
   - 支持直接从 Diff 页面一键跳转至编辑器，修改保存后实时通过 WebSocket 双向流同步回电脑磁盘。

---

## 🛠️ 电脑端 TUI 配置中心操作指南

在 Linux (Arch / Ubuntu 等) 或 Windows 终端中执行：
```bash
mircode config
# 或者
mircode tui
```
界面将进入交互式配置管理中心：
```text
╭────────────────── MirCode 配置管理中心 (Settings TUI) ──────────────────╮
│                                                                        │
│  ▶ 🚀 开机自启动 (Auto-start on boot)  : [ 已开启 ]      [回车/空格切换] │
│    ⚙️ 后台服务状态 (Service Control)   : ● 运行中        ['s'启停,'r'重启]│
│    🌐 服务监听端口 (Port)              : 18980          [回车修改]     │
│    🔑 配对安全令牌 (Token)             : 4b9e28f1a07c... [回车重新生成] │
│    📂 默认工作区 (Default Workspace)   : /home/neo/Code [回车修改]     │
│    ☁️ 异地中继服务器 (Relay Server)     : https://relay. [回车修改]     │
│    🐱 TailCat P2P 隧道 (Direct P2P)   : [ 已开启 ]      [回车/空格切换] │
│                                                                        │
│ ────────────────────────────────────────────────────────────────────── │
│ 提示: ✅ 已开启开机自启动 (systemd user 服务已启用)                      │
│                                                                        │
│ [↑/↓] 选择项目  |  [回车/空格] 切换/修改  |  [s] 启停服务  |  [q] 保存退出 │
╰────────────────────────────────────────────────────────────────────────╯
```

### 快捷按键
- **`↑` / `↓` 或 `k` / `j`**：移动光标选择配置项
- **`Enter` / `Space`**：切换开机自启动开关、TailCat 开关、或进入文本编辑
- **`s`**：一键启动 / 停止当前伴侣后台守护服务
- **`r`**：一键重启伴侣后台服务
- **`q` 或 `Esc`**：保存所有更改并退出 TUI

---

## 📦 安装指南 (Installation)

### Arch Linux 用户 (推荐 AUR)

MirCode 已发布至 **Arch User Repository (AUR)**，推荐使用 `yay` 或 `paru` 快速安装：

```bash
# 使用 yay 安装预编译二进制包
yay -S mircode-bin

# 或使用 paru 安装
paru -S mircode-bin
```
> 🔗 AUR 软件包主页: [https://aur.archlinux.org/packages/mircode-bin](https://aur.archlinux.org/packages/mircode-bin)

亦可从 [GitHub Releases](https://github.com/LaT-SKY/MirCode/releases) 下载官方构建的 `pkg.tar.zst` 使用 pacman 安装：
```bash
sudo pacman -U mircode-1.0.0-1-x86_64.pkg.tar.zst
```

### Windows / macOS / 其他 Linux 发行版
从 [GitHub Releases](https://github.com/LaT-SKY/MirCode/releases) 下载对应平台的二进制压缩包（解压即可运行，无需额外依赖）：
- Windows: `mircode-v1.0.0-windows-amd64.zip`
- Linux (x86_64): `mircode-v1.0.0-linux-amd64.tar.gz`
- Linux (ARM64): `mircode-v1.0.0-linux-arm64.tar.gz`
- macOS (Apple Silicon): `mircode-v1.0.0-darwin-arm64.tar.gz`
- macOS (Intel): `mircode-v1.0.0-darwin-amd64.tar.gz`

---

## 🚀 电脑端日常启动 (Daemon / CLI)

#### 方案 A：命令行直接启动伴侣服务
安装或解压后，在目标项目目录执行：
```bash
# 进入目标项目并启动服务
mircode .
```

#### 方案 B：使用终端配置中心管理与自启动
```bash
# 打开交互式 TUI 配置面板（配置开机自启、端口、中继）
mircode config
# 或者
mircode tui
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
⚙️  提示: 运行 `mircode config` 或 `mircode tui` 打开配置管理中心 (开机自启/设置)
📱 安卓端扫码配对二维码 (在 MirCode App 扫码即连):
[  TERMINAL QR CODE RENDERED HERE  ]
==================================================================
```

---

## 📱 移动端连接指南 (Android)

1. 从 [GitHub Releases](https://github.com/LaT-SKY/MirCode/releases) 下载 `MirCode-v1.0.0.apk` 安装。
2. 打开 MirCode：
   - **方式一（推荐·异地直连）**：在首页直接输入终端显示的 **6 位配对码**（如 `839-102`），点击“一键免配置异地连接”，立即穿透连通！
   - **方式二（扫码直连）**：点击扫码识别电脑终端的二维码，一秒完成全部配对。
   - **方式三（局域网直连）**：切换到“局域网直连”标签页，输入内网 IP 进行连接。

---

## 📄 开源许可 (License)

本项目采用 [MIT License](LICENSE) 开源。欢迎提交 Issue 与 Pull Request 共同完善 MirCode！
