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
		noQRFlag      bool
		versionFlag   bool
	)

	flag.StringVar(&hostFlag, "host", "0.0.0.0", "Host address to bind")
	flag.IntVar(&portFlag, "port", 18980, "Port to listen on")
	flag.StringVar(&tokenFlag, "token", "", "Security auth token (auto-generated if empty)")
	flag.StringVar(&workspaceFlag, "workspace", "", "Initial workspace path (default: current directory)")
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

	addr := fmt.Sprintf("%s:%d", hostFlag, portFlag)
	httpServer := &http.Server{
		Addr:    addr,
		Handler: server.Handler(),
	}

	// Print banner and network connection info
	hostname, _ := os.Hostname()
	lanIPs, tailscaleIP := detectIPs()

	fmt.Println("==================================================================")
	fmt.Printf("   MirCode Companion Service  v%s\n", Version)
	fmt.Println("   Material Design 3 移动端代码审查与工作区伴侣服务")
	fmt.Println("==================================================================")
	fmt.Printf("📂 当前工作区: %s\n", absWorkspace)
	fmt.Printf("🔑 安全配对令牌 (Token): %s\n", token)
	fmt.Println("------------------------------------------------------------------")
	fmt.Println("🌐 可用连接地址:")
	if tailscaleIP != "" {
		fmt.Printf("   - Tailscale 跨网直连: http://%s:%d\n", tailscaleIP, portFlag)
	}
	for _, ip := range lanIPs {
		fmt.Printf("   - 局域网 Wi-Fi 直连 : http://%s:%d\n", ip, portFlag)
	}
	fmt.Printf("   - 本机测试连接     : http://127.0.0.1:%d\n", portFlag)
	fmt.Println("------------------------------------------------------------------")

	// Print QR Code for Android App fast pairing
	primaryIP := "127.0.0.1"
	if tailscaleIP != "" {
		primaryIP = tailscaleIP
	} else if len(lanIPs) > 0 {
		primaryIP = lanIPs[0]
	}

	pairingURL := fmt.Sprintf("mircode://connect?host=%s&port=%d&token=%s&name=%s", primaryIP, portFlag, token, hostname)
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
	fmt.Println("MirCode 伴侣服务已安全退出。")
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
				// Tailscale CGNAT range
				tailscaleIP = ipStr
			} else {
				lanIPs = append(lanIPs, ipStr)
			}
		}
	}
	return lanIPs, tailscaleIP
}
