@echo off
javac -encoding UTF-8 -cp "agent-platform-core/target/classes;C:/Users/WYZ/.m2/repository/com/fasterxml/jackson/core/jackson-databind/2.15.3/jackson-databind-2.15.3.jar;C:/Users/WYZ/.m2/repository/com/fasterxml/jackson/core/jackson-core/2.15.3/jackson-core-2.15.3.jar;C:/Users/WYZ/.m2/repository/com/fasterxml/jackson/core/jackson-annotations/2.15.3/jackson-annotations-2.15.3.jar" -d . TextAuditHandler.java
if %ERRORLEVEL% EQU 0 (
    jar cf text-audit-skill.jar com/example/TextAuditHandler.class
    echo BUILD OK
) else (
    echo BUILD FAILED
)
