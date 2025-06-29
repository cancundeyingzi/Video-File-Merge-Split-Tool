package main

import (
	"bufio"
	"encoding/binary"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"regexp"
	"runtime"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/fatih/color"
	"github.com/schollz/progressbar/v3"
	"github.com/spf13/cobra"
)

const (
	// v3格式魔术字节标记
	MAGIC_BYTES = "MERGEDv3"
	// 读写缓冲区大小 (1MB)
	BUFFER_SIZE = 1024 * 1024
	// 文件名最大长度
	MAX_FILENAME_LENGTH = 255
	// 魔术字节长度
	MAGIC_LENGTH = 8 // "MERGEDv3"
	// v3格式：文件大小字段长度（8字节）
	SIZE_LENGTH = 8 // uint64
	// 4字节长度字段（文件名长度）
	UINT32_LENGTH = 4
	// v3最小文件大小检查
	MIN_V3_FILE_SIZE = 24 // 最小元数据大小
)

var (
	// 颜色定义
	colorRed     = color.New(color.FgRed, color.Bold)
	colorGreen   = color.New(color.FgGreen, color.Bold)
	colorYellow  = color.New(color.FgYellow, color.Bold)
	colorBlue    = color.New(color.FgBlue, color.Bold)
	colorCyan    = color.New(color.FgCyan, color.Bold)
	colorMagenta = color.New(color.FgMagenta, color.Bold)

	// 开发模式标志
	devMode = false
)

// FileInfo 文件信息结构体
type FileInfo struct {
	Name string
	Size int64
	Path string
}

// DebugInfo v3格式调试信息
type DebugInfo struct {
	FileSize        int64
	MagicBytes      string
	AttachSize      uint64
	VideoSize       uint64
	FilenameLength  uint32
	Filename        string
	CalculatedPos   map[string]int64
	ValidationError string
}

// 打印横幅
func printBanner() {
	banner := `
 ╭─────────────────────────────────────────────────────────╮
 │                  🎬 视频文件合并拆分工具                    │
 │                   Video Merger & Splitter               │
 │                      Go Version v3.0                    │
 │                    支持超大文件 (8位长度)                   │
 ╰─────────────────────────────────────────────────────────╯
`
	colorCyan.Print(banner)
}

// 开发模式调试信息
func printDebugInfo(info *DebugInfo) {
	if !devMode {
		return
	}

	colorMagenta.Println("\n🔧 === 开发模式调试信息 ===")
	fmt.Printf("📁 文件大小: %d bytes (%s)\n", info.FileSize, formatFileSize(info.FileSize))

	if info.MagicBytes != "" {
		fmt.Printf("🏷️  魔术字节: '%s' (长度: %d)\n", info.MagicBytes, len(info.MagicBytes))
	}

	if info.AttachSize > 0 {
		fmt.Printf("📎 附加文件大小: %d bytes (%s)\n", info.AttachSize, formatFileSize(int64(info.AttachSize)))
	}

	if info.VideoSize > 0 {
		fmt.Printf("🎬 视频文件大小: %d bytes (%s)\n", info.VideoSize, formatFileSize(int64(info.VideoSize)))
	}

	if info.FilenameLength > 0 {
		fmt.Printf("📝 文件名长度: %d\n", info.FilenameLength)
	}

	if info.Filename != "" {
		fmt.Printf("📄 文件名: '%s'\n", info.Filename)
	}

	if len(info.CalculatedPos) > 0 {
		fmt.Println("📍 计算位置:")
		for key, pos := range info.CalculatedPos {
			fmt.Printf("   %s: %d\n", key, pos)
		}
	}

	if info.ValidationError != "" {
		colorRed.Printf("❌ 验证错误: %s\n", info.ValidationError)
	}

	colorMagenta.Println("🔧 === 调试信息结束 ===\n")
}

// 清理和解析拖拽的文件路径
func parseDroppedPath(input string) string {
	// 移除前后空白
	path := strings.TrimSpace(input)

	// 移除可能的引号
	if len(path) >= 2 {
		if (strings.HasPrefix(path, `"`) && strings.HasSuffix(path, `"`)) ||
			(strings.HasPrefix(path, `'`) && strings.HasSuffix(path, `'`)) {
			path = path[1 : len(path)-1]
		}
	}

	// Windows 路径处理
	if runtime.GOOS == "windows" {
		// 处理反斜杠
		path = strings.ReplaceAll(path, `\`, `/`)
	}

	return path
}

// 读取用户输入
func readUserInput(prompt string) string {
	colorBlue.Print(prompt)
	reader := bufio.NewReader(os.Stdin)
	input, _ := reader.ReadString('\n')
	return strings.TrimSpace(input)
}

// 确认操作
func confirmAction(message string) bool {
	response := readUserInput(fmt.Sprintf("%s (y/N): ", message))
	response = strings.ToLower(response)
	return response == "y" || response == "yes"
}

// 显示文件信息预览
func showFilePreview(filePath string) error {
	info, err := validateFile(filePath)
	if err != nil {
		return err
	}

	fmt.Printf("📁 文件: %s\n", info.Name)
	fmt.Printf("📊 大小: %s\n", formatFileSize(info.Size))
	fmt.Printf("📍 路径: %s\n", info.Path)

	// 尝试检测文件类型
	ext := strings.ToLower(filepath.Ext(info.Name))
	var fileType string
	switch ext {
	case ".mp4", ".mkv", ".avi", ".mov", ".wmv", ".webm", ".flv":
		fileType = "🎬 视频文件"
	default:
		fileType = "📎 其他文件"
	}
	fmt.Printf("🏷️ 类型: %s\n", fileType)

	return nil
}

// 检测是否为v3合并文件
func isMergedFile(filePath string) bool {
	file, err := os.Open(filePath)
	if err != nil {
		return false
	}
	defer file.Close()

	info, err := file.Stat()
	if err != nil {
		return false
	}

	// 文件必须足够大：最小v3文件大小
	if info.Size() < MIN_V3_FILE_SIZE {
		colorBlue.Printf("ℹ️  文件太小，未检测到合并标记\n")
		return false
	}

	// 读取文件末尾的魔术字节
	magicBuffer := make([]byte, MAGIC_LENGTH)
	if _, err := file.Seek(-int64(MAGIC_LENGTH), 2); err != nil {
		colorBlue.Printf("ℹ️  无法读取文件末尾，未检测到合并标记\n")
		return false
	}

	if _, err := file.Read(magicBuffer); err != nil {
		colorBlue.Printf("ℹ️  读取失败，未检测到合并标记\n")
		return false
	}

	result := string(magicBuffer) == MAGIC_BYTES

	if result {
		colorGreen.Printf("✅ 检测到格式合并文件\n")
	} else {
		colorBlue.Printf("ℹ️  普通文件，未检测到合并标记\n")
	}

	return result
}

// 智能操作建议
func suggestOperation(filePath string) string {
	// 首先检查是否为合并文件
	if isMergedFile(filePath) {
		return "split"
	}

	// 如果不是合并文件，根据扩展名判断
	ext := strings.ToLower(filepath.Ext(filePath))
	switch ext {
	case ".mp4", ".mkv", ".avi", ".mov", ".wmv", ".webm":
		return "merge"
	default:
		return "merge"
	}
}

// 交互式合并操作
func interactiveMerge() error {
	colorMagenta.Println("\n🎬 === 文件合并模式 ===")
	fmt.Println("请按顺序提供两个文件：视频文件和要隐藏的附加文件")

	// 获取视频文件
	var videoPath string
	for {
		colorCyan.Println("\n📹 步骤 1: 请拖拽视频文件到此窗口，然后按回车:")
		input := readUserInput("视频文件路径> ")
		if input == "" {
			colorYellow.Println("⚠️ 路径不能为空，请重新拖拽文件")
			continue
		}

		videoPath = parseDroppedPath(input)
		fmt.Printf("\n解析路径: %s\n", videoPath)

		if err := showFilePreview(videoPath); err != nil {
			colorRed.Printf("❌ 文件错误: %v\n", err)
			if !confirmAction("是否重新选择文件？") {
				return fmt.Errorf("用户取消操作")
			}
			continue
		}

		if confirmAction("确认使用此视频文件？") {
			break
		}
	}

	// 获取附加文件
	var attachPath string
	for {
		colorCyan.Println("\n📎 步骤 2: 请拖拽要隐藏的文件到此窗口，然后按回车:")
		input := readUserInput("附加文件路径> ")
		if input == "" {
			colorYellow.Println("⚠️ 路径不能为空，请重新拖拽文件")
			continue
		}

		attachPath = parseDroppedPath(input)
		fmt.Printf("\n解析路径: %s\n", attachPath)

		if err := showFilePreview(attachPath); err != nil {
			colorRed.Printf("❌ 文件错误: %v\n", err)
			if !confirmAction("是否重新选择文件？") {
				return fmt.Errorf("用户取消操作")
			}
			continue
		}

		if confirmAction("确认使用此附加文件？") {
			break
		}
	}

	// 生成输出文件名
	videoInfo, _ := validateFile(videoPath)
	defaultOutput := strings.TrimSuffix(videoInfo.Name, filepath.Ext(videoInfo.Name)) + "_merged_v3" + filepath.Ext(videoInfo.Name)

	colorCyan.Printf("\n💾 步骤 3: 输出文件名 (默认: %s)\n", defaultOutput)
	outputName := readUserInput("输出文件名 (直接回车使用默认): ")
	if outputName == "" {
		outputName = defaultOutput
	}

	// 最终确认
	fmt.Printf("\n📋 操作摘要:\n")
	fmt.Printf("  🎬 视频文件: %s\n", filepath.Base(videoPath))
	fmt.Printf("  📎 附加文件: %s\n", filepath.Base(attachPath))
	fmt.Printf("  💾 输出文件: %s\n", outputName)

	if !confirmAction("确认开始格式合并？") {
		return fmt.Errorf("用户取消操作")
	}

	return mergeFiles(videoPath, attachPath, outputName)
}

// 交互式拆分操作
func interactiveSplit() error {
	colorMagenta.Println("\n📦 === 文件拆分模式 ===")
	fmt.Println("请提供一个格式合并后的文件进行拆分")

	// 获取合并文件
	var mergedPath string
	for {
		colorCyan.Println("\n📥 请拖拽合并后的文件到此窗口，然后按回车:")
		input := readUserInput("合并文件路径> ")
		if input == "" {
			colorYellow.Println("⚠️ 路径不能为空，请重新拖拽文件")
			continue
		}

		mergedPath = parseDroppedPath(input)
		fmt.Printf("\n解析路径: %s\n", mergedPath)

		if err := showFilePreview(mergedPath); err != nil {
			colorRed.Printf("❌ 文件错误: %v\n", err)
			if !confirmAction("是否重新选择文件？") {
				return fmt.Errorf("用户取消操作")
			}
			continue
		}

		// 检测是否为v3合并文件
		if !isMergedFile(mergedPath) {
			colorYellow.Println("⚠️ 这个文件看起来不是格式合并文件")
			if devMode || confirmAction("是否进入开发模式尝试解析？") {
				devMode = true
				colorMagenta.Println("🔧 已启用开发模式，将显示详细调试信息")
			}
			if !confirmAction("继续尝试拆分？") {
				continue
			}
		}

		if confirmAction("确认使用此文件进行拆分？") {
			break
		}
	}

	// 获取输出目录
	defaultOutputDir := "extracted_v3"
	colorCyan.Printf("\n📁 输出目录 (默认: %s)\n", defaultOutputDir)
	outputDir := readUserInput("输出目录 (直接回车使用默认): ")
	if outputDir == "" {
		outputDir = defaultOutputDir
	}

	// 最终确认
	fmt.Printf("\n📋 操作摘要:\n")
	fmt.Printf("  📦 合并文件: %s\n", filepath.Base(mergedPath))
	fmt.Printf("  📁 输出目录: %s\n", outputDir)
	fmt.Printf("  🔧 开发模式: %v\n", devMode)

	if !confirmAction("确认开始格式拆分？") {
		return fmt.Errorf("用户取消操作")
	}

	return splitFiles(mergedPath, outputDir)
}

// 智能文件处理
func smartFileHandler() error {
	colorMagenta.Println("\n🎯 === 智能文件处理模式 ===")
	fmt.Println("拖拽任意文件，程序将自动判断最适合的操作")

	for {
		colorCyan.Println("\n📁 请拖拽文件到此窗口 (输入 'q' 退出, 'dev' 切换开发模式):")
		input := readUserInput("文件路径> ")

		if input == "q" || input == "quit" || input == "exit" {
			return nil
		}

		if input == "dev" || input == "debug" {
			devMode = !devMode
			if devMode {
				colorMagenta.Println("🔧 开发模式已启用，将显示详细调试信息")
			} else {
				colorBlue.Println("🔧 开发模式已禁用")
			}
			continue
		}

		if input == "" {
			colorYellow.Println("⚠️ 路径不能为空，请重新拖拽文件")
			continue
		}

		filePath := parseDroppedPath(input)
		fmt.Printf("\n📍 解析路径: %s\n", filePath)

		if err := showFilePreview(filePath); err != nil {
			colorRed.Printf("❌ 文件错误: %v\n", err)
			continue
		}

		// 添加分隔线
		fmt.Println()

		// 智能建议操作
		suggested := suggestOperation(filePath)

		// 根据检测结果提供操作建议
		fmt.Println() // 确保有空行分隔

		if suggested == "split" {
			colorGreen.Println("💡 建议操作：拆分文件（提取隐藏内容）")
			outputDir := "extracted_v3_" + strings.TrimSuffix(filepath.Base(filePath), filepath.Ext(filePath))
			fmt.Println()
			err := splitFiles(filePath, outputDir)
			if err != nil {
				colorRed.Printf("❌ 拆分失败: %v\n", err)
				if !confirmAction("是否返回主菜单继续处理其他文件？") {
					return err
				}
			} else {
				if !confirmAction("拆分成功！是否继续处理其他文件？") {
					return nil
				}
			}
		} else {
			colorGreen.Println("💡 建议操作：格式合并文件")
			fmt.Println()
			err := interactiveMergeWithVideo(filePath)
			if err != nil {
				colorRed.Printf("❌ 合并失败: %v\n", err)
				if !confirmAction("是否返回主菜单继续处理其他文件？") {
					return err
				}
			} else {
				if !confirmAction("合并成功！是否继续处理其他文件？") {
					return nil
				}
			}
		}
	}

	return nil
}

// 预设视频文件的交互式合并
func interactiveMergeWithVideo(videoPath string) error {
	colorMagenta.Println("\n🎬 === 文件合并模式 (视频文件已选择) ===")

	fmt.Printf("✅ 视频文件: %s\n", filepath.Base(videoPath))

	// 获取附加文件
	var attachPath string
	for {
		colorCyan.Println("\n📎 请拖拽要隐藏的文件到此窗口，然后按回车:")
		input := readUserInput("附加文件路径> ")
		if input == "" {
			colorYellow.Println("⚠️ 路径不能为空，请重新拖拽文件")
			continue
		}

		attachPath = parseDroppedPath(input)
		fmt.Printf("\n解析路径: %s\n", attachPath)

		if err := showFilePreview(attachPath); err != nil {
			colorRed.Printf("❌ 文件错误: %v\n", err)
			if !confirmAction("是否重新选择文件？") {
				return fmt.Errorf("用户取消操作")
			}
			continue
		}
		break
	}

	// 生成输出文件名
	videoInfo, _ := validateFile(videoPath)
	defaultOutput := strings.TrimSuffix(videoInfo.Name, filepath.Ext(videoInfo.Name)) + "_merged_v3" + filepath.Ext(videoInfo.Name)

	colorCyan.Printf("\n💾 输出文件名 (默认: %s)\n", defaultOutput)
	outputName := readUserInput("输出文件名 (直接回车使用默认): ")
	if outputName == "" {
		outputName = defaultOutput
	}

	// 最终确认
	fmt.Printf("\n📋 操作摘要:\n")
	fmt.Printf("  🎬 视频文件: %s\n", filepath.Base(videoPath))
	fmt.Printf("  📎 附加文件: %s\n", filepath.Base(attachPath))
	fmt.Printf("  💾 输出文件: %s\n", outputName)

	return mergeFiles(videoPath, attachPath, outputName)
}

// 主交互界面
func interactiveMode() error {
	for {
		fmt.Println()
		colorMagenta.Println("🎯 === 主菜单 ===")
		fmt.Println("1. 📁 智能文件处理 (推荐)")
		fmt.Println("2. 🎬 合并文件")
		fmt.Println("3. 📦 拆分文件")
		fmt.Println("4. 🔧 切换开发模式")
		fmt.Println("5. ❓ 使用帮助")
		fmt.Println("6. 🚪 退出程序")

		fmt.Printf("当前模式: ")
		if devMode {
			colorMagenta.Printf("🔧 开发模式")
		} else {
			colorBlue.Printf("🎯 普通模式")
		}
		fmt.Println()

		choice := readUserInput("\n请选择操作 (1-6): ")

		switch choice {
		case "1":
			if err := smartFileHandler(); err != nil {
				colorRed.Printf("❌ 操作失败: %v\n", err)
				if !confirmAction("是否返回主菜单？") {
					return err
				}
			}
		case "2":
			if err := interactiveMerge(); err != nil {
				colorRed.Printf("❌ 合并失败: %v\n", err)
				if !confirmAction("是否返回主菜单？") {
					return err
				}
			}
		case "3":
			if err := interactiveSplit(); err != nil {
				colorRed.Printf("❌ 拆分失败: %v\n", err)
				if !confirmAction("是否返回主菜单？") {
					return err
				}
			}
		case "4":
			devMode = !devMode
			if devMode {
				colorMagenta.Println("🔧 开发模式已启用，将显示详细调试信息")
			} else {
				colorBlue.Println("🔧 开发模式已禁用")
			}
		case "5":
			showInteractiveHelp()
		case "6", "q", "quit", "exit":
			colorGreen.Println("\n👋 感谢使用！")
			return nil
		default:
			colorYellow.Printf("⚠️ 无效选择: %s\n", choice)
		}
	}
}

// 显示交互式帮助
func showInteractiveHelp() {
	fmt.Println()
	colorCyan.Println("📖 === 版本使用帮助 ===")
	fmt.Println()

	colorBlue.Println("🎯 智能文件处理:")
	fmt.Println("  • 直接拖拽任意文件到窗口")
	fmt.Println("  • 程序自动判断最适合的操作")
	fmt.Println("  • 合并文件→拆分，视频文件→合并")
	fmt.Println()

	colorBlue.Println("🎬 文件合并:")
	fmt.Println("  • 将任意文件隐藏到视频文件中")
	fmt.Println("  • 支持超大文件 (8字节大小字段)")
	fmt.Println("  • 生成格式，不兼容v1/v2")
	fmt.Println()

	colorBlue.Println("📦 文件拆分:")
	fmt.Println("  • 仅支持格式合并文件")
	fmt.Println("  • 超快固定位置读取")
	fmt.Println("  • 自动验证文件完整性")
	fmt.Println()

	colorBlue.Println("🔧 开发模式:")
	fmt.Println("  • 显示详细的格式解析信息")
	fmt.Println("  • 即使解析失败也显示调试数据")
	fmt.Println("  • 帮助诊断文件格式问题")
	fmt.Println()

	colorBlue.Println("💡 格式优势:")
	fmt.Println("  • 支持18EB超大文件")
	fmt.Println("  • 固定位置读取，极速解析")
	fmt.Println("  • 更严格的数据验证")
	fmt.Println("  • 简化的处理逻辑")

	readUserInput("\n按回车返回主菜单...")
}

// 验证并清理文件名
func validateAndCleanFilename(filename string) (string, error) {
	if filename == "" {
		return "", fmt.Errorf("文件名不能为空")
	}

	// 移除路径分隔符，只保留文件名部分
	filename = filepath.Base(filename)

	// 移除或替换非法字符
	reg := regexp.MustCompile(`[<>:"/\\|?*\x00-\x1f]`)
	cleaned := reg.ReplaceAllString(filename, "_")

	// 移除开头的点
	cleaned = strings.TrimLeft(cleaned, ".")

	// 限制长度
	if len(cleaned) > MAX_FILENAME_LENGTH {
		ext := filepath.Ext(cleaned)
		nameWithoutExt := strings.TrimSuffix(cleaned, ext)
		maxNameLength := MAX_FILENAME_LENGTH - len(ext)
		if maxNameLength > 0 {
			cleaned = nameWithoutExt[:maxNameLength] + ext
		} else {
			cleaned = cleaned[:MAX_FILENAME_LENGTH]
		}
	}

	// 确保UTF-8编码有效
	if !utf8.ValidString(cleaned) {
		return "", fmt.Errorf("文件名包含无效的UTF-8字符")
	}

	if cleaned == "" {
		return "", fmt.Errorf("处理后的文件名为空")
	}

	return cleaned, nil
}

// 验证文件
func validateFile(filePath string) (*FileInfo, error) {
	info, err := os.Stat(filePath)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, fmt.Errorf("文件不存在: %s", filePath)
		}
		return nil, fmt.Errorf("无法访问文件: %v", err)
	}

	if info.IsDir() {
		return nil, fmt.Errorf("不能处理目录: %s", filePath)
	}

	if info.Size() == 0 {
		return nil, fmt.Errorf("不能处理空文件: %s", filePath)
	}

	// 检查文件是否可读
	file, err := os.Open(filePath)
	if err != nil {
		return nil, fmt.Errorf("无法打开文件进行读取: %v", err)
	}
	file.Close()

	return &FileInfo{
		Name: info.Name(),
		Size: info.Size(),
		Path: filePath,
	}, nil
}

// 格式化文件大小
func formatFileSize(bytes int64) string {
	const unit = 1024
	if bytes < unit {
		return fmt.Sprintf("%d B", bytes)
	}
	div, exp := int64(unit), 0
	for n := bytes / unit; n >= unit; n /= unit {
		div *= unit
		exp++
	}
	return fmt.Sprintf("%.2f %cB", float64(bytes)/float64(div), "KMGTPE"[exp])
}

// 流式复制数据，带进度条
func copyWithProgress(dst io.Writer, src io.Reader, size int64, desc string) error {
	bar := progressbar.NewOptions64(size,
		progressbar.OptionSetDescription(desc),
		progressbar.OptionSetTheme(progressbar.Theme{
			Saucer:        "█",
			SaucerHead:    "█",
			SaucerPadding: "░",
			BarStart:      "[",
			BarEnd:        "]",
		}),
		progressbar.OptionShowBytes(true),
		progressbar.OptionSetWidth(50),
		progressbar.OptionShowCount(),
	)

	buffer := make([]byte, BUFFER_SIZE)
	var copied int64

	for {
		n, err := src.Read(buffer)
		if n > 0 {
			if _, writeErr := dst.Write(buffer[:n]); writeErr != nil {
				return fmt.Errorf("写入失败: %v", writeErr)
			}
			copied += int64(n)
			bar.Set64(copied)
		}
		if err == io.EOF {
			break
		}
		if err != nil {
			return fmt.Errorf("读取失败: %v", err)
		}
	}

	bar.Finish()
	return nil
}

// 格式合并文件
func mergeFiles(videoPath, attachPath, outputPath string) error {
	colorBlue.Println("\n📋 开始格式文件合并处理...")

	// 验证输入文件
	videoInfo, err := validateFile(videoPath)
	if err != nil {
		return fmt.Errorf("视频文件验证失败: %v", err)
	}

	attachInfo, err := validateFile(attachPath)
	if err != nil {
		return fmt.Errorf("附加文件验证失败: %v", err)
	}

	// 清理附加文件名
	cleanedAttachName, err := validateAndCleanFilename(attachInfo.Name)
	if err != nil {
		return fmt.Errorf("文件名处理失败: %v", err)
	}

	// 显示文件信息
	fmt.Printf("\n📹 视频文件: %s (%s)\n", videoInfo.Name, formatFileSize(videoInfo.Size))
	fmt.Printf("📎 附加文件: %s → %s (%s)\n", attachInfo.Name, cleanedAttachName, formatFileSize(attachInfo.Size))

	// 检查输出文件是否存在
	if _, err := os.Stat(outputPath); err == nil {
		colorYellow.Printf("⚠️  输出文件已存在: %s\n", outputPath)
		if !confirmAction("是否覆盖?") {
			return fmt.Errorf("用户取消操作")
		}
	}

	// 打开输入文件
	videoFile, err := os.Open(videoPath)
	if err != nil {
		return fmt.Errorf("无法打开视频文件: %v", err)
	}
	defer videoFile.Close()

	attachFile, err := os.Open(attachPath)
	if err != nil {
		return fmt.Errorf("无法打开附加文件: %v", err)
	}
	defer attachFile.Close()

	// 创建输出文件
	outputFile, err := os.Create(outputPath)
	if err != nil {
		return fmt.Errorf("无法创建输出文件: %v", err)
	}
	defer outputFile.Close()

	fmt.Println()

	// 1. 复制视频文件
	colorCyan.Println("🎬 复制视频文件...")
	if err := copyWithProgress(outputFile, videoFile, videoInfo.Size, "视频文件"); err != nil {
		return fmt.Errorf("复制视频文件失败: %v", err)
	}

	// 2. 复制附加文件
	colorCyan.Println("\n📎 复制附加文件...")
	if err := copyWithProgress(outputFile, attachFile, attachInfo.Size, "附加文件"); err != nil {
		return fmt.Errorf("复制附加文件失败: %v", err)
	}

	// 3. 写入格式元数据
	colorCyan.Println("\n🔮 写入格式元数据...")

	// 准备数据
	attachNameBytes := []byte(cleanedAttachName)

	// 格式：[文件名长度(4字节)] + [文件名] + [视频大小(8字节)] + [附加文件大小(8字节)] + [MERGEDv3(8字节)]

	// 写入文件名长度(4字节,小端)
	nameLengthBytes := make([]byte, UINT32_LENGTH)
	binary.LittleEndian.PutUint32(nameLengthBytes, uint32(len(attachNameBytes)))
	if _, err := outputFile.Write(nameLengthBytes); err != nil {
		return fmt.Errorf("写入文件名长度失败: %v", err)
	}

	// 写入文件名
	if _, err := outputFile.Write(attachNameBytes); err != nil {
		return fmt.Errorf("写入文件名失败: %v", err)
	}

	// 写入视频大小(8字节,小端)
	videoSizeBytes := make([]byte, SIZE_LENGTH)
	binary.LittleEndian.PutUint64(videoSizeBytes, uint64(videoInfo.Size))
	if _, err := outputFile.Write(videoSizeBytes); err != nil {
		return fmt.Errorf("写入视频大小失败: %v", err)
	}

	// 写入附加文件大小(8字节,小端)
	attachSizeBytes := make([]byte, SIZE_LENGTH)
	binary.LittleEndian.PutUint64(attachSizeBytes, uint64(attachInfo.Size))
	if _, err := outputFile.Write(attachSizeBytes); err != nil {
		return fmt.Errorf("写入附加文件大小失败: %v", err)
	}

	// 写入魔术字节（格式）
	if _, err := outputFile.WriteString(MAGIC_BYTES); err != nil {
		return fmt.Errorf("写入魔术字节失败: %v", err)
	}

	// 获取输出文件信息
	outputInfo, _ := os.Stat(outputPath)

	// 获取输出文件的绝对路径
	absOutputPath, err := filepath.Abs(outputPath)
	if err != nil {
		absOutputPath = outputPath
	}

	totalMetadataSize := UINT32_LENGTH + len(attachNameBytes) + SIZE_LENGTH + SIZE_LENGTH + MAGIC_LENGTH

	colorGreen.Printf("\n✅ 格式合并完成!\n")
	fmt.Printf("📊 合并统计:\n")
	fmt.Printf("   视频文件: %s\n", formatFileSize(videoInfo.Size))
	fmt.Printf("   附加文件: %s\n", formatFileSize(attachInfo.Size))
	fmt.Printf("   元数据: %s\n", formatFileSize(int64(totalMetadataSize)))
	fmt.Printf("   总大小: %s\n", formatFileSize(outputInfo.Size()))
	fmt.Printf("📁 输出文件: %s\n", filepath.Base(outputPath))
	colorCyan.Printf("📍 完整路径: %s\n", absOutputPath)

	return nil
}

// 格式拆分文件
func splitFiles(mergedPath, outputDir string) error {
	colorBlue.Println("\n📋 开始格式文件拆分处理...")

	// 验证输入文件
	mergedInfo, err := validateFile(mergedPath)
	if err != nil {
		return fmt.Errorf("合并文件验证失败: %v", err)
	}

	fmt.Printf("\n📦 合并文件: %s (%s)\n", mergedInfo.Name, formatFileSize(mergedInfo.Size))

	// 创建调试信息
	debugInfo := &DebugInfo{
		FileSize:      mergedInfo.Size,
		CalculatedPos: make(map[string]int64),
	}

	// 创建输出目录
	if err := os.MkdirAll(outputDir, 0755); err != nil {
		return fmt.Errorf("无法创建输出目录: %v", err)
	}

	// 打开合并文件
	mergedFile, err := os.Open(mergedPath)
	if err != nil {
		return fmt.Errorf("无法打开合并文件: %v", err)
	}
	defer mergedFile.Close()

	fmt.Println()
	colorCyan.Println("📖 解析格式元数据...")

	// 格式固定位置读取
	var attachSize uint64
	var videoSize uint64
	var nameLength uint32
	var attachName string

	// 尝试读取格式数据，即使出错也要显示调试信息
	defer func() {
		if devMode {
			// 更新调试信息
			debugInfo.AttachSize = attachSize
			debugInfo.VideoSize = videoSize
			debugInfo.FilenameLength = nameLength
			debugInfo.Filename = attachName
			printDebugInfo(debugInfo)
		}
	}()

	// 1. 验证文件大小
	if mergedInfo.Size < MIN_V3_FILE_SIZE {
		debugInfo.ValidationError = fmt.Sprintf("文件太小: %d < %d", mergedInfo.Size, MIN_V3_FILE_SIZE)
		return fmt.Errorf("文件太小，不是有效的格式文件")
	}

	// 2. 读取魔术字节（末尾9字节）
	magicBuffer := make([]byte, MAGIC_LENGTH)
	magicPos := mergedInfo.Size - int64(MAGIC_LENGTH)
	debugInfo.CalculatedPos["magic_bytes"] = magicPos

	if _, err := mergedFile.Seek(magicPos, 0); err != nil {
		debugInfo.ValidationError = fmt.Sprintf("无法定位魔术字节: %v", err)
		return fmt.Errorf("定位魔术字节失败: %v", err)
	}

	if _, err := mergedFile.Read(magicBuffer); err != nil {
		debugInfo.ValidationError = fmt.Sprintf("读取魔术字节失败: %v", err)
		return fmt.Errorf("读取魔术字节失败: %v", err)
	}

	debugInfo.MagicBytes = string(magicBuffer)
	if string(magicBuffer) != MAGIC_BYTES {
		debugInfo.ValidationError = fmt.Sprintf("魔术字节不匹配: 期望'%s', 实际'%s'", MAGIC_BYTES, string(magicBuffer))
		return fmt.Errorf("不是格式文件，魔术字节验证失败")
	}

	// 3. 读取附加文件大小（末尾-17到末尾-9，8字节）
	attachSizePos := mergedInfo.Size - int64(MAGIC_LENGTH+SIZE_LENGTH)
	debugInfo.CalculatedPos["attach_size"] = attachSizePos

	if _, err := mergedFile.Seek(attachSizePos, 0); err != nil {
		debugInfo.ValidationError = fmt.Sprintf("定位附加文件大小失败: %v", err)
		return fmt.Errorf("定位附加文件大小失败: %v", err)
	}

	attachSizeBytes := make([]byte, SIZE_LENGTH)
	if _, err := mergedFile.Read(attachSizeBytes); err != nil {
		debugInfo.ValidationError = fmt.Sprintf("读取附加文件大小失败: %v", err)
		return fmt.Errorf("读取附加文件大小失败: %v", err)
	}

	attachSize = binary.LittleEndian.Uint64(attachSizeBytes)

	// 4. 读取视频大小（末尾-25到末尾-17，8字节）
	videoSizePos := mergedInfo.Size - int64(MAGIC_LENGTH+SIZE_LENGTH*2)
	debugInfo.CalculatedPos["video_size"] = videoSizePos

	if _, err := mergedFile.Seek(videoSizePos, 0); err != nil {
		debugInfo.ValidationError = fmt.Sprintf("定位视频大小失败: %v", err)
		return fmt.Errorf("定位视频大小失败: %v", err)
	}

	videoSizeBytes := make([]byte, SIZE_LENGTH)
	if _, err := mergedFile.Read(videoSizeBytes); err != nil {
		debugInfo.ValidationError = fmt.Sprintf("读取视频大小失败: %v", err)
		return fmt.Errorf("读取视频大小失败: %v", err)
	}

	videoSize = binary.LittleEndian.Uint64(videoSizeBytes)

	// 5. 验证大小的合理性
	if videoSize == 0 || videoSize >= uint64(mergedInfo.Size) {
		debugInfo.ValidationError = fmt.Sprintf("视频大小异常: %d", videoSize)
		return fmt.Errorf("格式：视频文件大小异常: %d", videoSize)
	}

	if attachSize == 0 || attachSize >= uint64(mergedInfo.Size) {
		debugInfo.ValidationError = fmt.Sprintf("附加文件大小异常: %d", attachSize)
		return fmt.Errorf("格式：附加文件大小异常: %d", attachSize)
	}

	// 6. 计算并读取文件名
	// 文件名开始位置 = 视频大小 + 附加文件大小
	metadataStart := int64(videoSize + attachSize)
	debugInfo.CalculatedPos["metadata_start"] = metadataStart

	if _, err := mergedFile.Seek(metadataStart, 0); err != nil {
		debugInfo.ValidationError = fmt.Sprintf("定位文件名失败: %v", err)
		return fmt.Errorf("定位文件名失败: %v", err)
	}

	// 读取文件名长度（4字节）
	nameLengthBytes := make([]byte, UINT32_LENGTH)
	if _, err := mergedFile.Read(nameLengthBytes); err != nil {
		debugInfo.ValidationError = fmt.Sprintf("读取文件名长度失败: %v", err)
		return fmt.Errorf("读取文件名长度失败: %v", err)
	}

	nameLength = binary.LittleEndian.Uint32(nameLengthBytes)

	// 验证文件名长度
	if nameLength == 0 || nameLength > MAX_FILENAME_LENGTH {
		debugInfo.ValidationError = fmt.Sprintf("文件名长度异常: %d", nameLength)
		return fmt.Errorf("格式：文件名长度异常: %d", nameLength)
	}

	// 读取文件名
	nameBytes := make([]byte, nameLength)
	if _, err := mergedFile.Read(nameBytes); err != nil {
		debugInfo.ValidationError = fmt.Sprintf("读取文件名失败: %v", err)
		return fmt.Errorf("读取文件名失败: %v", err)
	}

	attachName = string(nameBytes)

	// 验证文件名
	if !utf8.ValidString(attachName) {
		debugInfo.ValidationError = "文件名包含无效的UTF-8字符"
		return fmt.Errorf("文件名包含无效的UTF-8字符")
	}

	// 7. 验证总体文件结构
	expectedFileSize := videoSize + attachSize + uint64(UINT32_LENGTH) + uint64(nameLength) + uint64(SIZE_LENGTH*2) + uint64(MAGIC_LENGTH)
	if expectedFileSize != uint64(mergedInfo.Size) {
		debugInfo.ValidationError = fmt.Sprintf("文件结构验证失败: 期望%d, 实际%d", expectedFileSize, mergedInfo.Size)
		return fmt.Errorf("格式：文件结构验证失败: 期望大小%d，实际大小%d", expectedFileSize, mergedInfo.Size)
	}

	fmt.Printf("\n📊 格式检测结果:\n")
	fmt.Printf("   🎬 视频文件: %s\n", formatFileSize(int64(videoSize)))
	fmt.Printf("   📎 附加文件: %s (%s)\n", attachName, formatFileSize(int64(attachSize)))
	fmt.Printf("   ✅ 格式结构验证通过\n")

	// 生成输出文件名
	videoName := strings.TrimSuffix(mergedInfo.Name, filepath.Ext(mergedInfo.Name))
	if strings.HasSuffix(videoName, "_merged_v3") {
		videoName = strings.TrimSuffix(videoName, "_merged_v3")
	} else if strings.HasSuffix(videoName, "_merged") {
		videoName = strings.TrimSuffix(videoName, "_merged")
	}

	// 尝试保持原始扩展名，如果没有则使用.mp4
	videoExt := filepath.Ext(mergedInfo.Name)
	if videoExt == "" {
		videoExt = ".mp4"
	}
	videoName += videoExt

	videoOutputPath := filepath.Join(outputDir, videoName)
	attachOutputPath := filepath.Join(outputDir, attachName)

	// 检查输出文件是否存在
	for _, path := range []string{videoOutputPath, attachOutputPath} {
		if _, err := os.Stat(path); err == nil {
			colorYellow.Printf("⚠️  文件已存在: %s\n", path)
			if !confirmAction("是否覆盖?") {
				return fmt.Errorf("用户取消操作")
			}
		}
	}

	fmt.Println()

	// 提取视频文件
	colorCyan.Println("🎬 提取视频文件...")
	if _, err := mergedFile.Seek(0, 0); err != nil {
		return fmt.Errorf("定位视频文件失败: %v", err)
	}

	videoFile, err := os.Create(videoOutputPath)
	if err != nil {
		return fmt.Errorf("创建视频文件失败: %v", err)
	}
	defer videoFile.Close()

	if err := copyWithProgress(videoFile, io.LimitReader(mergedFile, int64(videoSize)), int64(videoSize), "视频文件"); err != nil {
		return fmt.Errorf("提取视频文件失败: %v", err)
	}

	// 提取附加文件
	colorCyan.Println("\n📎 提取附加文件...")
	if _, err := mergedFile.Seek(int64(videoSize), 0); err != nil {
		return fmt.Errorf("定位附加文件失败: %v", err)
	}

	attachFile, err := os.Create(attachOutputPath)
	if err != nil {
		return fmt.Errorf("创建附加文件失败: %v", err)
	}
	defer attachFile.Close()

	if err := copyWithProgress(attachFile, io.LimitReader(mergedFile, int64(attachSize)), int64(attachSize), "附加文件"); err != nil {
		return fmt.Errorf("提取附加文件失败: %v", err)
	}

	// 获取输出文件的绝对路径
	absVideoPath, err := filepath.Abs(videoOutputPath)
	if err != nil {
		absVideoPath = videoOutputPath
	}

	absAttachPath, err := filepath.Abs(attachOutputPath)
	if err != nil {
		absAttachPath = attachOutputPath
	}

	absOutputDir, err := filepath.Abs(outputDir)
	if err != nil {
		absOutputDir = outputDir
	}

	colorGreen.Printf("\n✅ 格式拆分完成!\n")
	fmt.Printf("📊 拆分统计:\n")
	fmt.Printf("   🎬 视频文件: %s (%s)\n", videoName, formatFileSize(int64(videoSize)))
	fmt.Printf("   📎 附加文件: %s (%s)\n", attachName, formatFileSize(int64(attachSize)))
	fmt.Printf("📁 输出目录: %s\n", outputDir)
	colorCyan.Printf("📍 目录完整路径: %s\n", absOutputDir)
	fmt.Println("\n📄 输出文件完整路径:")
	colorCyan.Printf("   🎬 视频: %s\n", absVideoPath)
	colorCyan.Printf("   📎 附加: %s\n", absAttachPath)

	return nil
}

// 合并命令
var mergeCmd = &cobra.Command{
	Use:   "merge <video_file> <attach_file> <output_file>",
	Short: "格式合并视频文件和附加文件",
	Long: `将一个视频文件和一个任意文件合并成一个格式的新文件。
格式支持超大文件（8字节大小字段），不兼容v1/v2格式。`,
	Args: cobra.ExactArgs(3),
	RunE: func(cmd *cobra.Command, args []string) error {
		return mergeFiles(args[0], args[1], args[2])
	},
}

// 拆分命令
var splitCmd = &cobra.Command{
	Use:   "split <merged_file> [output_dir]",
	Short: "拆分格式合并后的文件",
	Long: `从格式合并后的文件中提取原始的视频文件和隐藏的附加文件。
仅支持格式，使用固定位置快速解析。
如果不指定输出目录，则在当前目录下创建extracted_目录。`,
	Args: cobra.RangeArgs(1, 2),
	RunE: func(cmd *cobra.Command, args []string) error {
		outputDir := "extracted_"
		if len(args) > 1 {
			outputDir = args[1]
		}
		return splitFiles(args[0], outputDir)
	},
}

// 交互式命令
var interactiveCmd = &cobra.Command{
	Use:     "interactive",
	Aliases: []string{"i", "inter"},
	Short:   "启动交互式模式",
	Long: `启动交互式模式，支持拖拽文件到命令行窗口进行操作。



推荐：初次使用或需要处理大文件的用户`,
	RunE: func(cmd *cobra.Command, args []string) error {
		return interactiveMode()
	},
}

// 根命令
var rootCmd = &cobra.Command{
	Use:   "video-merger-v3",
	Short: "视频文件合并拆分工具",
	Long: `🎬 视频文件合并拆分工具

这是一个命令行工具，可以将任意文件隐藏在视频文件中，
或者从格式合并后的文件中提取原始文件。

格式特性:
  ✅ 支持超大文件（8字节大小字段）
  ✅ 固定位置读取，极速解析  
  ✅ 智能文件名处理
  ✅ 详细的进度显示
  ✅ 完善的错误处理和调试
  🎯 交互式模式，支持拖拽文件
  🔧 开发模式，详细调试信息

快速开始:
  1. 交互模式: video-merger-v3 interactive
  2. 直接合并: video-merger-v3 merge video.mp4 secret.txt output_v3.mp4
  3. 直接拆分: video-merger-v3 split output_v3.mp4`,
	RunE: func(cmd *cobra.Command, args []string) error {
		// 如果没有参数，默认启动交互模式
		colorYellow.Println("💡 未指定操作，启动交互式模式...")
		colorYellow.Println("   提示：下次可以直接使用 'video-merger-v3 interactive'")
		time.Sleep(1 * time.Second)
		return interactiveMode()
	},
}

func init() {
	rootCmd.AddCommand(mergeCmd)
	rootCmd.AddCommand(splitCmd)
	rootCmd.AddCommand(interactiveCmd)

	// 添加开发模式标志
	rootCmd.PersistentFlags().BoolVarP(&devMode, "dev", "d", false, "启用开发模式，显示详细调试信息")
}

func main() {
	// 设置banner显示逻辑
	rootCmd.PersistentPreRun = func(cmd *cobra.Command, args []string) {
		// 只在交互模式或根命令时显示banner
		if cmd.Name() == "interactive" || cmd.Name() == "video-merger-v3" {
			printBanner()
		}

		// 显示开发模式状态
		if devMode {
			colorMagenta.Println("🔧 开发模式已启用")
		}
	}

	if err := rootCmd.Execute(); err != nil {
		colorRed.Printf("\n❌ 错误: %v\n", err)

		// 如果是交互模式的错误，提供重试选项
		if strings.Contains(err.Error(), "用户取消") {
			colorYellow.Println("💡 提示：可以随时重新运行程序")
		}

		os.Exit(1)
	}
}
