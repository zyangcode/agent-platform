package com.ls.agent.core.model.api;

@FunctionalInterface
public interface ModelStreamCallback {

    void onToken(String token);
}
