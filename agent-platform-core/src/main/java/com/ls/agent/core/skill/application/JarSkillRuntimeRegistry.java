package com.ls.agent.core.skill.application;

import com.ls.agent.core.skill.api.JarSkillHandler;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JarSkillRuntimeRegistry {

    private final Map<String, LoadedJarSkill> handlers = new ConcurrentHashMap<>();

    public void register(String skillCode, JarSkillHandler handler) {
        register(skillCode, handler, null);
    }

    public void register(String skillCode, JarSkillHandler handler, URLClassLoader classLoader) {
        if (skillCode == null || skillCode.isBlank()) {
            throw new IllegalArgumentException("skillCode must not be blank");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler must not be null");
        }
        LoadedJarSkill previous = handlers.put(skillCode, new LoadedJarSkill(handler, classLoader));
        close(previous);
    }

    public Optional<JarSkillHandler> find(String skillCode) {
        return Optional.ofNullable(handlers.get(skillCode)).map(LoadedJarSkill::handler);
    }

    @PreDestroy
    public void closeAll() {
        handlers.values().forEach(this::close);
        handlers.clear();
    }

    private void close(LoadedJarSkill loaded) {
        if (loaded == null || loaded.classLoader() == null) {
            return;
        }
        try {
            loaded.classLoader().close();
        } catch (IOException ignored) {
            // Registry cleanup must not break skill replacement or test teardown.
        }
    }

    private record LoadedJarSkill(JarSkillHandler handler, URLClassLoader classLoader) {
    }
}
