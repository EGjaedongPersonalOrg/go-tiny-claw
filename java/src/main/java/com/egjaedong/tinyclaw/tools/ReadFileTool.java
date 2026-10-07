package com.egjaedong.tinyclaw.tools;


import static com.openai.core.ObjectMappers.jsonMapper;

import com.egjaedong.tinyclaw.schema.ToolDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

public class ReadFileTool implements BaseTool {

    private final String name;
    private final String description;
    private final String workDir;
    private final String inputSchema;

    public ReadFileTool(String workDir) {
        this.name = "read_file";
        this.description = "读取指定路径的文件内容。请提供相对工作区的路径。";
        this.workDir = workDir;
        this.inputSchema = """
                        {
                          "type": "object",
                          "properties": {
                            "path": {
                                "type": "string",
                                "description": "要读取的文件路径，如 cmd/claw/main.java"
                            }
                          },
                          "required": ["path"]
                        }
                """;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public ToolDefinition getDefinition() {
        return new ToolDefinition(this.name, this.description, this.inputSchema);
    }

    @Override
    public ExecResult execute(String arguments) {
        ReadFileArgs input;
        try {
            input = jsonMapper().readValue(arguments, ReadFileArgs.class);
        } catch (JsonProcessingException e) {
            return new ExecResult("参数解析失败：" + e.getMessage(), false);
        }

        if (input.path == null || input.path.isBlank()) {
            return new ExecResult("路径为空，未读取任何内容。", false);
        }
        var fullPath = Paths.get(this.workDir).resolve(input.path);

        var maxLen = 8000;
        byte[] bytes;
        try (InputStream is = java.nio.file.Files.newInputStream(fullPath)) {
            bytes = is.readNBytes(maxLen);
        } catch (Exception e) {
            return new ExecResult("读取文件失败：" + e.getMessage(), false);
        }
        var content = new String(bytes, StandardCharsets.UTF_8);
        var contentResult = content;
        if (bytes.length >= maxLen) {
            contentResult = String.format("%s\n\n...[由于内容过长，已被系统截断至前 %d 字节...]",
                    content, maxLen);
        }
        return new ExecResult(contentResult, true);
    }

    private record ReadFileArgs(String path) {

    }
}