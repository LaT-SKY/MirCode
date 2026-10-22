package tui

import (
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"strconv"
	"strings"

	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
	"github.com/LaT-SKY/MirCode/companion/pkg/config"
)

type ConfigField int

const (
	FieldAutoStart ConfigField = iota
	FieldServiceState
	FieldPort
	FieldToken
	FieldWorkspace
	FieldRelayURL
	FieldTailcat
	fieldCount
)

type ConfigModel struct {
	cfg        *config.Config
	cursor     ConfigField
	editing    bool
	editBuffer string
	statusMsg  string
	width      int
	height     int
	isRunning  bool
}

func NewConfigModel() (ConfigModel, error) {
	cfg, err := config.Load()
	if err != nil {
		cfg = config.DefaultConfig()
	}

	isRunning := config.IsServiceRunning(cfg.Port)

	return ConfigModel{
		cfg:       cfg,
		cursor:    FieldAutoStart,
		isRunning: isRunning,
		statusMsg: "使用方向键上下移动，回车/空格切换或修改配置，按 'q' 保存退出",
	}, nil
}

func (m ConfigModel) Init() tea.Cmd {
	return nil
}

func (m ConfigModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		return m, nil

	case tea.KeyMsg:
		if m.editing {
			switch msg.Type {
			case tea.KeyEnter:
				m.commitEdit()
				m.editing = false
				_ = config.Save(m.cfg)
				return m, nil
			case tea.KeyEsc:
				m.editing = false
				m.statusMsg = "已取消编辑"
				return m, nil
			case tea.KeyBackspace:
				if len(m.editBuffer) > 0 {
					m.editBuffer = m.editBuffer[:len(m.editBuffer)-1]
				}
				return m, nil
			case tea.KeyRunes, tea.KeySpace:
				m.editBuffer += msg.String()
				return m, nil
			}
			return m, nil
		}

		// Navigation & Actions
		switch msg.String() {
		case "q", "ctrl+c", "esc":
			_ = config.Save(m.cfg)
			return m, tea.Quit

		case "up", "k":
			if m.cursor > 0 {
				m.cursor--
			} else {
				m.cursor = fieldCount - 1
			}
			return m, nil

		case "down", "j":
			if m.cursor < fieldCount-1 {
				m.cursor++
			} else {
				m.cursor = 0
			}
			return m, nil

		case "enter", " ":
			return m.handleAction()

		case "s":
			// Start/Stop service
			if m.isRunning {
				_ = config.ServiceAction("stop")
				m.isRunning = false
				m.statusMsg = "已发送停止服务指令"
			} else {
				_ = config.ServiceAction("start")
				m.isRunning = true
				m.statusMsg = "已发送启动服务指令"
			}
			return m, nil

		case "r":
			// Restart service
			_ = config.ServiceAction("restart")
			m.isRunning = true
			m.statusMsg = "已重启 MirCode 伴侣后台服务"
			return m, nil
		}
	}

	return m, nil
}

func (m *ConfigModel) handleAction() (tea.Model, tea.Cmd) {
	switch m.cursor {
	case FieldAutoStart:
		newState := !m.cfg.AutoStart
		err := config.SetAutoStart(newState)
		if err != nil {
			m.statusMsg = fmt.Sprintf("设置开机自启失败: %v", err)
		} else {
			m.cfg.AutoStart = newState
			if newState {
				m.statusMsg = "✅ 已开启开机自启动 (systemd user 服务已启用)"
			} else {
				m.statusMsg = "已关闭开机自启动"
			}
			_ = config.Save(m.cfg)
		}

	case FieldServiceState:
		if m.isRunning {
			_ = config.ServiceAction("stop")
			m.isRunning = false
			m.statusMsg = "伴侣服务已停止"
		} else {
			_ = config.ServiceAction("start")
			m.isRunning = true
			m.statusMsg = "伴侣服务已在后台启动"
		}

	case FieldPort:
		m.editing = true
		m.editBuffer = strconv.Itoa(m.cfg.Port)
		m.statusMsg = "输入新端口号后按回车保存 (Esc 取消)"

	case FieldToken:
		// Regenerate random token
		b := make([]byte, 16)
		_, _ = rand.Read(b)
		m.cfg.Token = hex.EncodeToString(b)
		_ = config.Save(m.cfg)
		m.statusMsg = "✅ 已生成新配对令牌并已保存"

	case FieldWorkspace:
		m.editing = true
		m.editBuffer = m.cfg.DefaultWorkspace
		m.statusMsg = "输入新的默认工作区路径后按回车保存"

	case FieldRelayURL:
		m.editing = true
		m.editBuffer = m.cfg.RelayURL
		m.statusMsg = "输入中继服务器地址后按回车保存"

	case FieldTailcat:
		m.cfg.TailcatEnabled = !m.cfg.TailcatEnabled
		_ = config.Save(m.cfg)
		if m.cfg.TailcatEnabled {
			m.statusMsg = "TailCat P2P 隧道已启用"
		} else {
			m.statusMsg = "TailCat P2P 隧道已禁用"
		}
	}

	return *m, nil
}

func (m *ConfigModel) commitEdit() {
	switch m.cursor {
	case FieldPort:
		if p, err := strconv.Atoi(strings.TrimSpace(m.editBuffer)); err == nil && p > 0 && p < 65536 {
			m.cfg.Port = p
			m.statusMsg = fmt.Sprintf("端口已修改为: %d", p)
		} else {
			m.statusMsg = "无效的端口号，修改未保存"
		}
	case FieldWorkspace:
		m.cfg.DefaultWorkspace = strings.TrimSpace(m.editBuffer)
		m.statusMsg = "默认工作区已更新"
	case FieldRelayURL:
		m.cfg.RelayURL = strings.TrimSpace(m.editBuffer)
		m.statusMsg = "中继服务器地址已更新"
	}
}

func (m ConfigModel) View() string {
	boxWidth := 72
	if m.width > 20 && m.width < 76 {
		boxWidth = m.width - 4
	}

	headerStyle := lipgloss.NewStyle().
		Bold(true).
		Foreground(lipgloss.Color("#FFFFFF")).
		Background(lipgloss.Color("#6750A4")).
		Padding(0, 2)

	cardStyle := lipgloss.NewStyle().
		Border(lipgloss.RoundedBorder()).
		BorderForeground(lipgloss.Color("#6750A4")).
		Padding(1, 2).
		Width(boxWidth)

	var sb strings.Builder
	sb.WriteString(headerStyle.Render(" MirCode 配置管理中心 (Settings TUI) ") + "\n\n")

	items := []struct {
		field ConfigField
		title string
		value string
		hint  string
	}{
		{
			field: FieldAutoStart,
			title: "🚀 开机自启动 (Auto-start on boot)",
			value: renderToggle(m.cfg.AutoStart),
			hint:  "[回车/空格切换]",
		},
		{
			field: FieldServiceState,
			title: "⚙️ 后台服务状态 (Service Control)",
			value: renderServiceState(m.isRunning),
			hint:  "['s' 启动/停止, 'r' 重启]",
		},
		{
			field: FieldPort,
			title: "🌐 服务监听端口 (Port)",
			value: strconv.Itoa(m.cfg.Port),
			hint:  "[回车修改]",
		},
		{
			field: FieldToken,
			title: "🔑 配对安全令牌 (Token)",
			value: truncate(m.cfg.Token, 24),
			hint:  "[回车重新生成]",
		},
		{
			field: FieldWorkspace,
			title: "📂 默认工作区 (Default Workspace)",
			value: truncate(m.cfg.DefaultWorkspace, 28),
			hint:  "[回车修改]",
		},
		{
			field: FieldRelayURL,
			title: "☁️ 异地中继服务器 (Relay Server)",
			value: truncate(m.cfg.RelayURL, 28),
			hint:  "[回车修改]",
		},
		{
			field: FieldTailcat,
			title: "🐱 TailCat P2P 隧道 (Direct P2P)",
			value: renderToggle(m.cfg.TailcatEnabled),
			hint:  "[回车/空格切换]",
		},
	}

	for _, item := range items {
		isSelected := m.cursor == item.field
		pointer := "  "
		itemStyle := lipgloss.NewStyle()
		if isSelected {
			pointer = "▶ "
			itemStyle = itemStyle.Bold(true).Foreground(lipgloss.Color("#6750A4"))
		}

		displayVal := item.value
		if m.editing && isSelected {
			displayVal = "[" + m.editBuffer + "█]"
		}

		line := fmt.Sprintf("%s%-36s : %-16s %s\n", pointer, item.title, displayVal, item.hint)
		sb.WriteString(itemStyle.Render(line))
	}

	sb.WriteString("\n" + strings.Repeat("─", boxWidth-6) + "\n")
	sb.WriteString(lipgloss.NewStyle().Foreground(lipgloss.Color("#00897B")).Bold(true).Render("提示: "+m.statusMsg) + "\n\n")

	sb.WriteString(lipgloss.NewStyle().Foreground(lipgloss.Color("#79747E")).Render(
		"[↑/↓] 选择项目  |  [回车/空格] 切换/修改  |  [s] 启停服务  |  [r] 重启服务  |  [q] 保存退出",
	))

	return cardStyle.Render(sb.String())
}

func renderToggle(enabled bool) string {
	if enabled {
		return lipgloss.NewStyle().Bold(true).Foreground(lipgloss.Color("#10B981")).Render("[ 已开启 ]")
	}
	return lipgloss.NewStyle().Foreground(lipgloss.Color("#79747E")).Render("[ 已关闭 ]")
}

func renderServiceState(running bool) string {
	if running {
		return lipgloss.NewStyle().Bold(true).Foreground(lipgloss.Color("#10B981")).Render("● 运行中 (Active)")
	}
	return lipgloss.NewStyle().Foreground(lipgloss.Color("#EF4444")).Render("○ 已停止 (Stopped)")
}

func truncate(s string, max int) string {
	if len(s) > max {
		return s[:max-3] + "..."
	}
	return s
}

// RunConfigTUI launches the settings TUI program.
func RunConfigTUI() error {
	m, err := NewConfigModel()
	if err != nil {
		return err
	}
	p := tea.NewProgram(m)
	_, err = p.Run()
	return err
}
