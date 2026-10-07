package com.egjaedong.tinyclaw;

import java.nio.file.Path;

import com.egjaedong.tinyclaw.engine.AgentEngine;
import com.egjaedong.tinyclaw.provider.LlmProvider;
import com.egjaedong.tinyclaw.provider.OpenaiProvider;
import com.egjaedong.tinyclaw.tools.BashTool;
import com.egjaedong.tinyclaw.tools.EditFileTool;
import com.egjaedong.tinyclaw.tools.ReadFileTool;
import com.egjaedong.tinyclaw.tools.Registry;
import com.egjaedong.tinyclaw.tools.RegistryImpl;
import com.egjaedong.tinyclaw.tools.WriteFileTool;

/**
 * 入口。对照 {@code go/cmd/claw/main.go}。
 *
 * <p>
 * 建议在这里组装 Provider、Registry、AgentEngine，然后发起一次任务。
 */
public final class Claw {

    public static void main(String[] args) {
        // 对照 os.Getwd()：进程 cwd，不是 jar 所在目录，也不是 git 根
        String workDir = Path.of("").toAbsolutePath().normalize().toString();
        LlmProvider llmProvider = new OpenaiProvider("deepseek-flash");
        Registry registry = new RegistryImpl();
        registry.register(new ReadFileTool(workDir));
        registry.register(new WriteFileTool(workDir));
        registry.register(new BashTool(workDir));
        registry.register(new EditFileTool(workDir));
        AgentEngine agentEngine = new AgentEngine(llmProvider, registry, workDir, false);
        String prompt = """
                我当前目录下有一个 server.go 文件。
                请帮我把里面 "TODO: 增加鉴权逻辑" 下面的那个 if 语句，整个替换为：
                if user == nil {
                   fmt.Println("Forbidden!")
                   return
                }
                              """;
        agentEngine.run(prompt);
        System.out.println("任务完成！");
    }
}
