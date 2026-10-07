package com.egjaedong.tinyclaw.tools;

import com.egjaedong.tinyclaw.schema.ToolDefinition;

public interface BaseTool {

    String getName();

    ToolDefinition getDefinition();

    ExecResult execute(String arguments);
}
