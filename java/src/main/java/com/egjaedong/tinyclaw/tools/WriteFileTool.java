package com.egjaedong.tinyclaw.tools;

import static com.openai.core.ObjectMappers.jsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;

import org.apache.commons.lang3.tuple.Pair;

import com.egjaedong.tinyclaw.schema.ToolDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;

public class WriteFileTool implements BaseTool {

    private final String name;
    private final String description;
    private final String workDir;
    private final String inputSchema;

    public WriteFileTool(String workDir) {
        this.name = "write_file";
        this.description = "创建或覆盖写入一个文件。如果目录不存在会自动创建。请提供相对于工作区的相对路径。";
        this.workDir = workDir;
        this.inputSchema = """
                            {
                                "type": "object",
                                "properties":
                                {
                                    "path":
                                    {
                                        "type": "string",
                                        "description": "要写入的文件路径，如 cmd/claw/main.java"
                                    },
                                    "content":
                                    {
                                        "type": "string",
                                        "description": "要写入的完整文件内容"
                                    }
                                },
                                "required":
                                [
                                    "path",
                                    "content"
                                ]
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
    public Pair<String, Boolean> execute(String arguments) {
        WriteFileArgs input;
        try {
            input = jsonMapper().readValue(arguments, WriteFileArgs.class);
        } catch (JsonProcessingException e) {
            return Pair.of("参数解析失败：" + e.getMessage(), false);
        }

        if (input.path() == null || input.path().isBlank()) {
            return Pair.of("路径为空，未写入任何内容。", false);
        }

        // 【安全防线】：限制在 WorkDir 下执行，防止大模型修改系统级文件
        var fullPath = Paths.get(this.workDir).resolve(input.path());

        // 自动创建缺失的父级目录
        try {
            Files.createDirectories(fullPath.getParent(),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxr-xr-x")));
        } catch (IOException e) {
            return Pair.of("创建父级目录失败:" + e.getMessage(), false);
        }

        // 写入文件内容，权限设置为 064
        try {
            Files.writeString(fullPath, input.content());
            Files.setPosixFilePermissions(fullPath, PosixFilePermissions.fromString("rw-r--r--"));
        } catch (Exception e) {
            return Pair.of("写入文件失败:" + e.getMessage(), false);
        }

        return Pair.of("成功写入文件" + input.path(), true);
    }

    private record WriteFileArgs(String path, String content) {
    }
}
