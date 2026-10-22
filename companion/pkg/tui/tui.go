package tui

import (
	"fmt"
	"strings"
	"sync"
	"time"

	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
	"github.com/skip2/go-qrcode"
	"github.com/LaT-SKY/MirCode/companion/pkg/api"
	"github.com/LaT-SKY/MirCode/companion/pkg/git"
	"github.com/LaT-SKY/MirCode/companion/pkg/relay"
	"github.com/LaT-SKY/MirCode/companion/pkg/review"
)

// EventItem represents a single log entry in the TUI stream.
type EventItem struct {
	Timestamp time.Time
	Type      string
	Message   string
	Color     lipgloss.Color
}

// Model is the Bubbletea state model for the TUI dashboard.
type Model struct {
	workspace        string
	branch           string
	pairCode         string
	token            string
	lanIP            string
	port             int
	tailcatAddr      string
	totalChanged     int
	totalReviewed    int
	connectedClients int
	events           []EventItem
	showQR           bool
	width            int
	height           int
	statusMsg        string
	relayClient      *relay.Client
	reviewMgr        *review.Manager
	apiServer        *api.Server
	mu               sync.RWMutex
}

// Msg types
type EventMsg EventItem
type StatusRefreshMsg struct {
	branch        string
	totalChanged  int
	totalReviewed int
}
type PairCodeMsg string

// NewModel creates the initial TUI model.
func NewModel(
	workspace string,
	pairCode string,
	token string,
	lanIP string,
	port int,
	tailcatAddr string,
	relayClient *relay.Client,
	reviewMgr *review.Manager,
	apiServer *api.Server,
) Model {
	return Model{
		workspace:   workspace,
		branch:      "main",
		pairCode:    pairCode,
		token:       token,
		lanIP:       lanIP,
		port:        port,
		tailcatAddr: tailcatAddr,
		relayClient: relayClient,
		reviewMgr:   reviewMgr,
		apiServer:   apiServer,
		events: []EventItem{
			{
				Timestamp: time.Now(),
				Type:      "SYSTEM",
				Message:   "MirCode 伴侣守护服务与 TUI 控制台已启动",
				Color:     lipgloss.Color("#10B981"),
			},
		},
		statusMsg: "就绪 - 等待安卓端接入",
	}
}

func (m Model) Init() tea.Cmd {
	return tea.Batch(
		tea.EnterAltScreen,
		m.refreshStatusCmd(),
	)
}

func (m Model) refreshStatusCmd() tea.Cmd {
	return func() tea.Msg {
		reviews := make(map[string]review.Item)
		if m.reviewMgr != nil {
			reviews = m.reviewMgr.GetAll()
		}
		st, err := git.GetStatus(m.workspace, reviews)
		if err != nil {
			return StatusRefreshMsg{branch: "unknown", totalChanged: 0, totalReviewed: 0}
		}
		return StatusRefreshMsg{
			branch:        st.Branch,
			totalChanged:  st.TotalChanged,
			totalReviewed: st.TotalReviewed,
		}
	}
}

func (m Model) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		return m, nil

	case tea.KeyMsg:
		switch msg.String() {
		case "q", "ctrl+c":
			return m, tea.Quit
		case "p":
			m.showQR = !m.showQR
			return m, nil
		case "r":
			m.statusMsg = "正在刷新工作区状态..."
			return m, m.refreshStatusCmd()
		}

	case EventMsg:
		m.events = append(m.events, EventItem(msg))
		if len(m.events) > 100 {
			m.events = m.events[len(m.events)-100:]
		}
		return m, m.refreshStatusCmd()

	case StatusRefreshMsg:
		m.branch = msg.branch
		m.totalChanged = msg.totalChanged
		m.totalReviewed = msg.totalReviewed
		m.statusMsg = "状态已更新"
		return m, nil

	case PairCodeMsg:
		m.pairCode = string(msg)
		return m, nil
	}

	return m, nil
}

func (m Model) View() string {
	if m.width == 0 {
		return "正在加载 MirCode 终端控制台..."
	}

	// Styles (Material Design 3 inspired purple & teal tones)
	titleStyle := lipgloss.NewStyle().
		Bold(true).
		Foreground(lipgloss.Color("#FFFFFF")).
		Background(lipgloss.Color("#6750A4")).
		Padding(0, 2)

	subHeaderStyle := lipgloss.NewStyle().
		Foreground(lipgloss.Color("#D0BCFF")).
		Padding(0, 1)

	boxStyle := lipgloss.NewStyle().
		Border(lipgloss.RoundedBorder()).
		BorderForeground(lipgloss.Color("#79747E")).
		Padding(1)

	accentStyle := lipgloss.NewStyle().
		Bold(true).
		Foreground(lipgloss.Color("#00897B"))

	// Header
	header := lipgloss.JoinHorizontal(
		lipgloss.Center,
		titleStyle.Render(" MirCode Companion Dashboard "),
		subHeaderStyle.Render(fmt.Sprintf("工作区: %s  |  分支: %s", m.workspace, m.branch)),
	)

	// Available width calculations
	contentWidth := m.width - 4
	if contentWidth < 40 {
		contentWidth = 40
	}
	leftWidth := contentWidth * 4 / 10
	rightWidth := contentWidth - leftWidth - 2

	// --- Left Panel: Connection & Pairing ---
	var leftSb strings.Builder
	leftSb.WriteString(accentStyle.Render("📱 移动端连接与配对") + "\n\n")

	displayCode := m.pairCode
	if len(displayCode) == 6 {
		displayCode = displayCode[:3] + " - " + displayCode[3:]
	}

	codeBlock := lipgloss.NewStyle().
		Bold(true).
		Foreground(lipgloss.Color("#6750A4")).
		Background(lipgloss.Color("#EADDFF")).
		Padding(0, 2).
		Render(" " + displayCode + " ")

	leftSb.WriteString(fmt.Sprintf("6 位免配置配对码:\n  %s\n\n", codeBlock))
	leftSb.WriteString(fmt.Sprintf("🔑 配对令牌: %s\n", m.token))
	leftSb.WriteString(fmt.Sprintf("🌐 Wi-Fi 地址: http://%s:%d\n", m.lanIP, m.port))
	if m.tailcatAddr != "" {
		leftSb.WriteString(fmt.Sprintf("🐱 TailCat P2P: %s\n", m.tailcatAddr))
	} else {
		leftSb.WriteString("🐱 TailCat P2P: 自动中继已待命\n")
	}

	leftSb.WriteString(fmt.Sprintf("\n📶 在线设备: %d 台安卓客户端\n", m.connectedClients))

	if m.showQR {
		leftSb.WriteString("\n[二维码配对] (按 'p' 折叠):\n")
		pairURL := fmt.Sprintf("mircode://connect?host=%s&port=%d&token=%s&code=%s", m.lanIP, m.port, m.token, m.pairCode)
		if qr, err := qrcode.New(pairURL, qrcode.Low); err == nil {
			leftSb.WriteString(qr.ToSmallString(false))
		}
	} else {
		leftSb.WriteString("\n(按 'p' 可展开终端二维码)\n")
	}

	leftPanel := boxStyle.Width(leftWidth).Render(leftSb.String())

	// --- Right Top Panel: Review Progress ---
	var rightTopSb strings.Builder
	rightTopSb.WriteString(accentStyle.Render("🔍 代码审查与工作区概览") + "\n\n")

	progressPercent := 0
	if m.totalChanged > 0 {
		progressPercent = (m.totalReviewed * 100) / m.totalChanged
	}

	barWidth := 20
	filled := (progressPercent * barWidth) / 100
	if filled > barWidth {
		filled = barWidth
	}
	bar := strings.Repeat("█", filled) + strings.Repeat("░", barWidth-filled)

	progressBar := lipgloss.NewStyle().Foreground(lipgloss.Color("#00897B")).Render(bar)
	rightTopSb.WriteString(fmt.Sprintf("审查进度: [%s] %d%% (%d / %d 文件)\n", progressBar, progressPercent, m.totalReviewed, m.totalChanged))
	rightTopSb.WriteString(fmt.Sprintf("Git 状态: %d 个待审查/变更文件\n", m.totalChanged))

	rightTopPanel := boxStyle.Width(rightWidth).Render(rightTopSb.String())

	// --- Right Bottom Panel: Live Event Stream ---
	var eventSb strings.Builder
	eventSb.WriteString(accentStyle.Render("⚡ 实时事件流 (Live Event Log)") + "\n\n")

	maxEvents := 8
	startIdx := len(m.events) - maxEvents
	if startIdx < 0 {
		startIdx = 0
	}

	for i := startIdx; i < len(m.events); i++ {
		ev := m.events[i]
		typeTag := lipgloss.NewStyle().
			Bold(true).
			Foreground(ev.Color).
			Render(fmt.Sprintf("[%s]", ev.Type))

		timeStr := ev.Timestamp.Format("15:04:05")
		eventSb.WriteString(fmt.Sprintf("%s %s %s\n", timeStr, typeTag, ev.Message))
	}

	rightBottomPanel := boxStyle.Width(rightWidth).Render(eventSb.String())

	rightPanel := lipgloss.JoinVertical(lipgloss.Left, rightTopPanel, rightBottomPanel)

	mainContent := lipgloss.JoinHorizontal(lipgloss.Top, leftPanel, rightPanel)

	// Bottom Help Bar
	footerStyle := lipgloss.NewStyle().
		Foreground(lipgloss.Color("#79747E")).
		Padding(0, 1)

	footer := footerStyle.Render(
		"[q] 退出  |  [p] 切换二维码展示  |  [r] 刷新状态  |  MirCode v1.0.1 (MD3 Protocol)",
	)

	return lipgloss.JoinVertical(lipgloss.Left, header, mainContent, footer)
}

// Global programmatic handle to dispatch TUI events
var (
	GlobalProgram *tea.Program
)

// LogEvent sends a log item to the running TUI program if active.
func LogEvent(eventType, message string, colorHex string) {
	if GlobalProgram == nil {
		return
	}
	color := lipgloss.Color(colorHex)
	if colorHex == "" {
		color = lipgloss.Color("#6750A4")
	}
	GlobalProgram.Send(EventMsg{
		Timestamp: time.Now(),
		Type:      eventType,
		Message:   message,
		Color:     color,
	})
}
