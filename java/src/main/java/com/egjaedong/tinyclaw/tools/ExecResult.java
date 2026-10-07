package com.egjaedong.tinyclaw.tools;

/** 工具层执行结果，给人看。Registry 再翻成 {@code ToolResult.isError} 给模型。 */
public record ExecResult(String output, boolean isSuccess) {
}
