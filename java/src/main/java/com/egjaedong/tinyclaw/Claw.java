package com.egjaedong.tinyclaw;

import com.egjaedong.tinyclaw.provider.OpenaiProvider;
import com.egjaedong.tinyclaw.tools.BashTool;
import com.egjaedong.tinyclaw.tools.ReadFileTool;
import com.egjaedong.tinyclaw.tools.RegistryImpl;
import com.egjaedong.tinyclaw.tools.WriteFileTool;

import java.nio.file.Path;

import com.egjaedong.tinyclaw.engine.AgentEngine;
import com.egjaedong.tinyclaw.provider.LlmProvider;
import com.egjaedong.tinyclaw.tools.Registry;

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
        AgentEngine agentEngine = new AgentEngine(llmProvider, registry, workDir, false);
        String prompt = """
                请帮我执行以下操作：
                1. 用 bash 查看一下我当前电脑的 Java 版本。
                2. 帮我写一个简单的 HelloWorld.java 文件，输出 "Hello, java-tiny-claw!"。
                3. 用 bash 编译并运行这个 java 文件，确认它能正常工作。
                               """;
        ;
        agentEngine.run(prompt);
        System.out.println("任务完成！");
    }
}
