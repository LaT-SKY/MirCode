package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"net"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"strings"
	"syscall"
	"time"

	"github.com/skip2/go-qrcode"
	"github.com/LaT-SKY/MirCode/companion/pkg/api"
	"github.com/LaT-SKY/MirCode/companion/pkg/auth"
	"github.com/LaT-SKY/MirCode/companion/pkg/relay"
	"github.com/LaT-SKY/MirCode/companion/pkg/tailcat"
)

var (
	Version   = "1.0.0"
	BuildTime = "2026-10-22"
)

func main() {
	var (
		hostFlag      string
		portFlag      int
		tokenFlag     string
		workspaceFlag string
		relayFlag     string
		noRelayFlag   bool
		noQRFlag      bool
		versionFlag   bool
	)

	flag.StringVar(&hostFlag, "host", "0.0.0.0", "Host address to bind")
	flag.IntVar(&portFlag, "port", 18980, "Port to listen on")
	flag.StringVar(&tokenFlag, "token", "", "Security auth token (auto-generated if empty)")
	flag.StringVar(&workspaceFlag, "workspace", "", "Initial workspace path (default: current directory)")
	flag.StringVar(&relayFlag, "relay", "https://relay.mircode.dev", "Relay server URL for zero-config remote connections")
	flag.BoolVar(&noRelayFlag, "no-relay", false, "Disable zero-config relay client")
	flag.BoolVar(&noQRFlag, "no-qr", false, "Disable printing QR code in terminal")
	flag.BoolVar(&versionFlag, "version", false, "Print version and exit")
	flag.Parse()

	if versionFlag {
		fmt.Printf("MirCode Companion v%s (%s)\n", Version, BuildTime)
		os.Exit(0)
	}

	// Positional argument for workspace path: `mircode .` or `mircode /path/to/dir`
	if workspaceFlag == "" {
		if flag.NArg() > 0 {
			workspaceFlag = flag.Arg(0)
		} else {
			workspaceFlag = "."
		}
	}

	absWorkspace, err := filepath.Abs(workspaceFlag)
	if err != nil {
		log.Fatalf("Invalid workspace path: %v", err)
	}

	if fi, err := os.Stat(absWorkspace); err != nil || !fi.IsDir() {
		log.Fatalf("Workspace must be an existing directory: %s", absWorkspace)
	}

	authMgr := auth.NewManager(tokenFlag)
	token := authMgr.GetToken()

	server, err := api.NewServer(absWorkspace, authMgr)
	if err != nil {
		log.Fatalf("Failed to initialize server: %v", err)
	}
	defer server.Close()

	handler := server.Handler()
	addr := fmt.Sprintf("%s:%d", hostFlag, portFlag)
	httpServer := &http.Server{
		Addr:    addr,
		Handler: handler,
	}

	// Tailcat integration check
	tailcatMgr := tailcat.NewManager()
	var tailcatAddr string
	if tailcatMgr.IsAvailable() {
		addr, err := tailcatMgr.Start(portFlag)
		if err == nil {
			tailcatAddr = addr
		}
	}

	// Zero-config Relay client setup
	var relayClient *relay.Client
	pairCodeCh := make(chan string, 1)

	if !noRelayFlag {
		relayClient = relay.NewClient(relayFlag, token, handler, func(code string) {
			select {
			case pairCodeCh <- code:
			default:
			}
		})
		relayClient.Start()
		defer relayClient.Stop()
	}

	// Wait briefly (up to 1.5s) for relay code
	var activePairCode string
	select {
	case code := <-pairCodeCh:
		activePairCode = code
	case <-time.After(1500 * time.Millisecond):
		activePairCode = "连接中..."
	}

	hostname, _ := os.Hostname()
	lanIPs, _ := detectIPs()

	fmt.Println("==================================================================")
	fmt.Printf("   MirCode Companion Service  v%s\n", Version)
	fmt.Println("   Material Design 3 移动端代码审查与工作区伴侣服务")
	fmt.Println("==================================================================")
	fmt.Printf("📂 当前工作区: %s\n", absWorkspace)
	fmt.Printf("🔑 安全配对令牌 (Token): %s\n", token)
	fmt.Println("------------------------------------------------------------------")
	fmt.Println("🚀 异地免配置连接 (无需同一 Wi-Fi，随时随地连接):")
	if activePairCode != "连接中..." && activePairCode != "" {
		fmt.Printf("   🔢 6 位专属配对码 : 【 %s 】(手机 App 输入即连)\n", formatCode(activePairCode))
	}
	if tailcatAddr != "" {
		fmt.Printf("   🐱 Tailcat P2P 隧道: %s\n", tailcatAddr)
	} else {
		fmt.Println("   🐱 Tailcat P2P 隧道: 备用中继已就绪 (安装 tailcat CLI 可加速 P2P 直连)")
	}
	fmt.Println("------------------------------------------------------------------")
	fmt.Println("🌐 局域网直连地址 (同一 Wi-Fi):")
	for _, ip := range lanIPs {
		fmt.Printf("   - Wi-Fi 内网地址   : http://%s:%d\n", ip, portFlag)
	}
	fmt.Printf("   - 本机调试地址     : http://127.0.0.1:%d\n", portFlag)
	fmt.Println("------------------------------------------------------------------")

	primaryIP := "127.0.0.1"
	if len(lanIPs) > 0 {
		primaryIP = lanIPs[0]
	}

	pairingURL := fmt.Sprintf(
		"mircode://connect?host=%s&port=%d&token=%s&name=%s&code=%s&relay=%s&tailcat=%s",
		primaryIP, portFlag, token, hostname, activePairCode, relayFlag, tailcatAddr,
	)

	if !noQRFlag {
		fmt.Println("📱 安卓端扫码配对二维码 (在 MirCode App 扫码即连):")
		qr, err := qrcode.New(pairingURL, qrcode.Medium)
		if err == nil {
			fmt.Println(qr.ToSmallString(false))
		}
	}
	fmt.Println("==================================================================")
	fmt.Println("提示: 按 Ctrl+C 停止伴侣服务")

	// Start server in background goroutine
	go func() {
		if err := httpServer.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("Server Listen error: %v", err)
		}
	}()

	// Wait for shutdown signal
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	fmt.Println("\n正在关闭 MirCode 伴侣服务...")
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()

	_ = httpServer.Shutdown(ctx)
	if tailcatMgr != nil {
		tailcatMgr.Stop()
	}
	fmt.Println("MirCode 伴侣服务已安全退出。")
}

func formatCode(code string) string {
	if len(code) == 6 {
		return code[:3] + " - " + code[3:]
	}
	return code
}

func detectIPs() (lanIPs []string, tailscaleIP string) {
	ifaces, err := net.Interfaces()
	if err != nil {
		return nil, ""
	}

	for _, iface := range ifaces {
		if iface.Flags&net.FlagUp == 0 || iface.Flags&net.FlagLoopback != 0 {
			continue
		}
		addrs, err := iface.Addrs()
		if err != nil {
			continue
		}
		for _, addr := range addrs {
			var ip net.IP
			switch v := addr.(type) {
			case *net.IPNet:
				ip = v.IP
			case *net.IPAddr:
				ip = v.IP
			}
			if ip == nil || ip.IsLoopback() || ip.To4() == nil {
				continue
			}

			ipStr := ip.String()
			if strings.HasPrefix(ipStr, "100.") {
				tailscaleIP = ipStr
			} else {
				lanIPs = append(lanIPs, ipStr)
			}
		}
	}
	return lanIPs, tailscaleIP
}
