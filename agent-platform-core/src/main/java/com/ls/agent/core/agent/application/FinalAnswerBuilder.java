package com.ls.agent.core.agent.application;

public class FinalAnswerBuilder extends SingleAgentFinalResponseSynthesizer {

    public String build(String assistantMessage) {
        return cleanUserVisibleAnswer(assistantMessage);
    }
}
