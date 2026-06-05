package com.ls.agent.core.agent.hook;

import com.ls.agent.core.model.dto.ModelInvokeResult;

public interface AgentRuntimeHook {

    default void preModelCall(ModelHookContext context) {
    }

    default void postModelCall(ModelHookContext context, ModelInvokeResult result, Exception error) {
    }

    default void preToolCall(ToolHookContext context) {
    }

    default void postToolCall(ToolHookContext context, String output, Exception error) {
    }

    default void postFinalAnswer(FinalAnswerHookContext context) {
    }
}
