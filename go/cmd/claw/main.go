// cmd/claw/main.go
package main

import (
	"context"
	"log"
	"os"

	"github.com/EGjaedong/go-tiny-claw/internal/engine"
	"github.com/EGjaedong/go-tiny-claw/internal/provider"
	"github.com/EGjaedong/go-tiny-claw/internal/tools"
	"github.com/EGjaedong/go-tiny-claw/internal/util"
)

// 3. 组装运行
func main() {
	// 确保能读取到 ENV
	util.LoadDotEnv()

	if os.Getenv("DASHSCOPE_API_KEY") == "" {
		log.Fatal("环境变量加载失败")
	}

	// 获取当前执行目录作为 WorkDir 物理边界
	workDir, _ := os.Getwd()

	// 1.初始化真实的 Provider
	// 可以切换不同的 Provider 试试，当然当前只有 openai 和 A畜 的两种
	llmProvider := provider.NewDashscopeOpenAIProvider("deepseek-flash")
	// llmProvider := provider.NewDashscopeClaudeProvider("qwen3.8-max")

	// 2. 注册工具集
	registry := tools.NewRegistry()
	registry.Register(tools.NewReadFileTool(workDir))
	registry.Register(tools.NewWriteFileTool(workDir))
	registry.Register(tools.NewBashTool(workDir))

	registry.Register(tools.NewEditFileTool(workDir))

	// 3. 实例化核心引擎，任务简单，关闭 thinking
	eng := engine.NewAgentEngine(llmProvider, registry, workDir, false)

	// 设定测试任务
	prompt := `
	我当前目录下有一个 server.go 文件。 
	请帮我把里面 "TODO: 增加鉴权逻辑" 下面的那个 if 语句，整个替换为： 
	if user == nil { 
		fmt.Println("Forbidden!") 
		return 
	}
	`

	// 发起任务指令
	err := eng.Run(context.Background(), prompt)
	if err != nil {
		log.Fatalf("引擎运行崩溃: %v", err)
	}
}
