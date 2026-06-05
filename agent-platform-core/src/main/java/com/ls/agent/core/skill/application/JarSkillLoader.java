package com.ls.agent.core.skill.application;

import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.skill.api.JarSkillHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class JarSkillLoader {

    private final JarSkillRuntimeRegistry runtimeRegistry;

    public JarSkillLoader(JarSkillRuntimeRegistry runtimeRegistry) {
        this.runtimeRegistry = runtimeRegistry;
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
