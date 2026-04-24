package com.openclaw.tools;

import com.openclaw.common.error.ErrorCode;

/** Error codes owned by the tools module family. */
public enum ToolErrorCode implements ErrorCode {

    /** Tool requested by the model is not registered (or has been stripped by policy). */
    TOOL_NOT_FOUND("TOOL_4040", "Tool not registered"),
    /** Provided params fail JSON-schema validation (or normalization). */
    TOOL_PARAM_INVALID("TOOL_4000", "Tool parameters invalid"),
    /** Policy pipeline decided the tool must not be exposed to the current caller. */
    TOOL_DENIED("TOOL_4030", "Tool denied by policy"),
    /** A {@code before_tool_call} hook flipped {@code block=true}. */
    TOOL_BLOCKED_BY_HOOK("TOOL_4031", "Tool blocked by before_tool_call hook"),
    /** The tool's executor threw. */
    TOOL_EXECUTION_FAILED("TOOL_5000", "Tool execution failed"),
    /** Two tools registered with the same name under the same registry scope. */
    TOOL_NAME_CONFLICT("TOOL_4090", "Duplicate tool name");

    private final String code;
    private final String defaultMessage;

    ToolErrorCode(final String code, final String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
