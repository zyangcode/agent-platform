package com.ls.agent.core.skill.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.skill.api.JarSkillHandler;
import com.ls.agent.core.skill.entity.SkillArtifactEntity;
import com.ls.agent.core.skill.entity.SkillEntity;
import com.ls.agent.core.skill.entity.SkillVersionEntity;
import com.ls.agent.core.skill.mapper.SkillArtifactMapper;
import com.ls.agent.core.skill.mapper.SkillMapper;
import com.ls.agent.core.skill.mapper.SkillVersionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class JarSkillLoader {

    private static final Logger log = LoggerFactory.getLogger(JarSkillLoader.class);
    private static final String TYPE_JAR = "JAR";
    private static final String STATUS_ENABLED = "ENABLED";

    private final JarSkillRuntimeRegistry runtimeRegistry;
    private final SkillMapper skillMapper;
    private final SkillVersionMapper skillVersionMapper;
    private final SkillArtifactMapper skillArtifactMapper;

    public JarSkillLoader(JarSkillRuntimeRegistry runtimeRegistry) {
        this(runtimeRegistry, null, null, null);
    }

    @Autowired
    public JarSkillLoader(
            JarSkillRuntimeRegistry runtimeRegistry,
            SkillMapper skillMapper,
            SkillVersionMapper skillVersionMapper,
            SkillArtifactMapper skillArtifactMapper
    ) {
        this.runtimeRegistry = runtimeRegistry;
        this.skillMapper = skillMapper;
        this.skillVersionMapper = skillVersionMapper;
        this.skillArtifactMapper = skillArtifactMapper;
    }

    public void loadAndRegister(String skillCode, Path jarPath, String handlerClassName) {
        if (jarPath == null || !Files.isRegularFile(jarPath)) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "Jar artifact does not exist: " + jarPath);
        }
        if (handlerClassName == null || handlerClassName.isBlank()) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "Jar skill handlerClass must not be blank");
        }
        URLClassLoader classLoader = null;
        try {
            classLoader = new ChildFirstJarClassLoader(
                    new URL[]{jarPath.toUri().toURL()},
                    JarSkillHandler.class.getClassLoader()
            );
            Class<?> handlerClass = Class.forName(handlerClassName, true, classLoader);
            if (!JarSkillHandler.class.isAssignableFrom(handlerClass)) {
                throw new BizException(
                        ErrorCode.REQUEST_INVALID,
                        "Jar skill handlerClass must implement " + JarSkillHandler.class.getName()
                );
            }
            JarSkillHandler handler = (JarSkillHandler) handlerClass.getDeclaredConstructor().newInstance();
            runtimeRegistry.register(skillCode, handler, classLoader);
        } catch (BizException ex) {
            closeQuietly(classLoader);
            throw ex;
        } catch (Exception ex) {
            closeQuietly(classLoader);
            throw new BizException(ErrorCode.REQUEST_INVALID, "Jar skill load failed: " + safeMessage(ex));
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restoreEnabledJarSkillsOnStartup() {
        restoreEnabledJarSkills();
    }

    public void restoreEnabledJarSkills() {
        if (skillMapper == null || skillVersionMapper == null || skillArtifactMapper == null) {
            return;
        }
        var skills = skillMapper.selectList(new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getSkillType, TYPE_JAR)
                .eq(SkillEntity::getStatus, STATUS_ENABLED)
                .isNotNull(SkillEntity::getCurrentVersionId));
        for (SkillEntity skill : skills) {
            restoreOne(skill);
        }
    }

    private void restoreOne(SkillEntity skill) {
        try {
            SkillVersionEntity version = skillVersionMapper.selectById(skill.getCurrentVersionId());
            if (version == null) {
                log.warn("Skip Jar skill restore because current version is missing, skillCode={}, versionId={}",
                        skill.getCode(), skill.getCurrentVersionId());
                return;
            }
            String handlerClass = version.getRuntimeConfig() == null
                    ? ""
                    : version.getRuntimeConfig().path("handlerClass").asText("");
            SkillArtifactEntity artifact = skillArtifactMapper.selectOne(new LambdaQueryWrapper<SkillArtifactEntity>()
                    .eq(SkillArtifactEntity::getSkillVersionId, version.getId())
                    .eq(SkillArtifactEntity::getArtifactType, TYPE_JAR));
            if (artifact == null) {
                log.warn("Skip Jar skill restore because artifact is missing, skillCode={}, versionId={}",
                        skill.getCode(), version.getId());
                return;
            }
            loadAndRegister(skill.getCode(), Path.of(artifact.getStoragePath()), handlerClass);
            log.info("Restored Jar skill into runtime registry, skillCode={}, versionId={}",
                    skill.getCode(), version.getId());
        } catch (Exception ex) {
            log.warn("Failed to restore Jar skill into runtime registry, skillCode={}, reason={}",
                    skill.getCode(), safeMessage(ex));
        }
    }

    private void closeQuietly(URLClassLoader classLoader) {
        if (classLoader == null) {
            return;
        }
        try {
            classLoader.close();
        } catch (IOException ignored) {
            // Failed load cleanup must preserve the original validation error.
        }
    }

    private String safeMessage(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private static final class ChildFirstJarClassLoader extends URLClassLoader {

        private ChildFirstJarClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (mustDelegateToParent(name)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        loaded = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }

        private boolean mustDelegateToParent(String name) {
            return name.startsWith("java.")
                    || name.startsWith("javax.")
                    || name.startsWith("jakarta.")
                    || name.startsWith("com.ls.agent.core.skill.api.")
                    || name.startsWith("com.ls.agent.core.skill.command.")
                    || name.startsWith("com.ls.agent.core.skill.dto.")
                    || name.startsWith("com.fasterxml.jackson.");
        }
    }
}
