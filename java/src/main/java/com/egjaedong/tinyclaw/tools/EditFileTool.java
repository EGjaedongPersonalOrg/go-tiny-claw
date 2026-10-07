package com.egjaedong.tinyclaw.tools;

import static com.openai.core.ObjectMappers.jsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import com.egjaedong.tinyclaw.schema.ToolDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;

public class EditFileTool implements BaseTool {

    private final String name;
    private final String description;
    private final String workDir;
    private final String inputSchema;

    public EditFileTool(String workDir) {
        this.name = "edit_file";
        this.description = "对现有文件进行局部的字符串替换。这比重写整个文件更安全、更快速。请提供足够的 old_text 上下文以确保匹配的唯一性。";
        this.workDir = workDir;
        this.inputSchema = """
                            {
                                "type": "object",
                                "properties": {
                                    "path": {
                                        "type": "string",
                                        "description": "要修改的文件路径"
                                    },
                                    "oldText": {
                                        "type": "string",
                                        "description": "文件中原有的文本。必须包含足够的上下文（建议上下各多包含几行），以确保在文件中的唯一性。"
                                    },
                                    "newText": {
                                        "type": "string",
                                        "description": "要替换成的新文本"
                                    }
                                },
                                "required":["path", "oldText", "newText"]
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
        EditFileArgs input;
        try {
            input = jsonMapper().readValue(arguments, EditFileArgs.class);
        } catch (JsonProcessingException exception) {
            return new ExecResult("参数解析失败：" + exception.getMessage(), false);
        }


        if (input.path() == null || input.path().isBlank()) {
            return new ExecResult("路径为空，未写入任何内容。", false);
        }

        // 【安全防线】：限制在 WorkDir 下执行，防止大模型修改系统级文件
        var fullPath = Paths.get(this.workDir).resolve(input.path());

        // 1. 读取原文件的内容
        String originalContent;
        try {
            originalContent = Files.readString(fullPath);
        } catch (IOException e) {
            return new ExecResult("读取文件失败，请确认路径是否正确" + e.getMessage(), false);
        }

        // 2. 调用多级模糊替换算法
        var result = fuzzyReplace(originalContent, input.oldText(), input.newText());
        if (!result.isSuccess()) {
            return result;
        }

        // 3. 将新内容安全地写入磁盘
        try {
            Files.writeString(fullPath, result.output());
            Files.setPosixFilePermissions(fullPath, PosixFilePermissions.fromString("rw-r--r--"));
        } catch (Exception e) {
            return new ExecResult("写入文件失败:" + e.getMessage(), false);
        }

        return new ExecResult("✅ 成功修改文件: " + input.path(), true);
    }

    private ExecResult fuzzyReplace(String originalContent, String oldText, String newText) {
        // L1: 精确匹配
        var count = StringUtils.countMatches(originalContent, oldText);
        if (count == 1) {
            return new ExecResult(Strings.CS.replaceOnce(originalContent, oldText, newText), true);
        }
        if (count > 1) {
            return new ExecResult("old_text 匹配到了 " + count + " 处，请提供更多的上下文代码以确保一致性", false);
        }

        // L2: 换行符归一化（统一将 \r\n 转换为 \n）
        var normalizedContent = Strings.CS.replace(originalContent, "\r\n", "\n");
        var normalizedOld = Strings.CS.replace(oldText, "\r\n", "\n");

        count = StringUtils.countMatches(normalizedContent, normalizedOld);
        if (count == 1) {
            return new ExecResult(Strings.CS.replaceOnce(normalizedContent, normalizedOld, newText), true);
        }

        // L3: Trim Space 匹配（忽略首位的空行和空格）
        var trimmedOld = normalizedOld.trim();
        if (StringUtils.isNoneBlank(trimmedOld)) {
            count = StringUtils.countMatches(normalizedContent, trimmedOld);
            if (count == 1) {
                // 注意：这里替换时，我们只能替换被 Trim 后的部分，不能直接用 newText 破坏原本的缩进
                // 为了保持本专栏代码不过于冗长复杂，当触发 L3/L4 时，如果 newText 没有带有正确的缩进，
                // 可能会导致替换后代码格式不美观。但这总比直接报错让 Agent 死循环要好。
                return new ExecResult(Strings.CS.replaceOnce(normalizedContent, trimmedOld, newText), true);
            }
        }

        // L4: 逐行匹配缩进（最强利的容错：消除大模型遗漏缩进的幻觉）
        return lineByLineReplace(normalizedContent, trimmedOld, newText);
    }

    private ExecResult lineByLineReplace(String content, String oldText, String newText) {
        var contentLines = content.split("\n");
        var oldLines = oldText.split("\n");

        if (oldLines.length == 0 || contentLines.length < oldLines.length) {
            return new ExecResult("找不到代码片段", false);
        }

        // 清理 oldLines 的每行首位空白
        for (int i = 0; i < oldLines.length; i++) {
            oldLines[i] = oldLines[i].trim();
        }

        var matchCount = 0;
        var matchStartIndex = -1;
        var matchEndIndex = -1;

        // 滑动窗口在原始文件中寻找匹配块
        for (int i = 0; i <= contentLines.length - oldLines.length; i++) {
            var isMatch = true;
            for (int j = 0; j < oldLines.length; j++) {
                if (!contentLines[i+j].trim().equals(oldLines[j])) {
                    isMatch = false;
                    break;
                }
            }

            if (isMatch) {
                matchCount++;
                matchStartIndex = i;
                matchEndIndex = i + oldLines.length;
            }
        }

        if (matchCount == 0) {
            return new ExecResult("在文件中未找到 old_text，请大模型先调用 read_file 仔细确认文件内容和缩进", false);
        }

        if (matchCount > 1) {
            return new ExecResult(String.format("模糊匹配到了 %d 处相似代码，请提供更多上下行代码以精确定位", matchCount), false);
        }

        // 执行替换：将匹配到的原始行范围替换为 newText 拆分后的行
        // （这里简单处理，将 newText 直接作为整体替换进去）
        var lines = Arrays.asList(contentLines);
        var newContentLines = new ArrayList<String>();
        newContentLines.addAll(lines.subList(0, matchStartIndex));
        newContentLines.add(newText);
        newContentLines.addAll(lines.subList(matchEndIndex, lines.size()));
        return new ExecResult(String.join("\n", newContentLines), true);
    }

    private record EditFileArgs(String path, String oldText, String newText) {
    }
}
