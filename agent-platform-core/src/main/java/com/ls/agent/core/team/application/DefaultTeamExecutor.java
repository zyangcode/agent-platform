package com.ls.agent.core.team.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.agent.tool.AgentToolDTO;
import com.ls.agent.core.agent.tool.AgentToolDispatchCommand;
import com.ls.agent.core.agent.tool.AgentToolDispatchResult;
import com.ls.agent.core.agent.tool.AgentToolDispatcher;
import com.ls.agent.core.model.api.ModelInvokeService;
import com.ls.agent.core.model.command.ModelInvokeCommand;
import com.ls.agent.core.model.dto.ModelInvokeResult;
import com.ls.agent.core.model.dto.ModelMessage;
import com.ls.agent.core.profile.dto.ProfileDTO;
import com.ls.agent.core.team.api.TeamExecutor;
import com.ls.agent.core.team.command.ExecuteTeamTaskCommand;
import com.ls.agent.core.team.dto.ExecutionResultDTO;
import com.ls.agent.core.team.dto.TeamTaskDTO;
import com.ls.agent.core.team.dto.TeamTaskExecutionResultDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DefaultTeamExecutor implements TeamExecutor {

    private static final String TASK_TYPE_TOOL = "TOOL_TASK";
    private static final String TASK_TYPE_MODEL = "MODEL_TASK";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_SKIPPED = "SKIPPED";
    private static final BigDecimal MODEL_TASK_TEMPERATURE = BigDecimal.valueOf(0.4);

    private final AgentToolDispatcher toolDispatcher;
    private final ModelInvokeService modelInvokeService;

    public DefaultTeamExecutor(AgentToolDispatcher toolDispatcher, ModelInvokeService modelInvokeService) {
        this.toolDispatcher = toolDispatcher;
        this.modelInvokeService = modelInvokeService;
    }

    @Override
    public TeamTaskExecutionResultDTO execute(ExecuteTeamTaskCommand command) {
        validate(command);
        Optional<ExecutionResultDTO> dependencyFailure = firstDependencyFailure(command);
        if (dependencyFailure.isPresent()) {
            String dependencyId = dependencyFailure.get().taskId();
            return result(skipped(command.task(), "dependsOn task " + dependencyId + " is not successful"));
        }

        if (TASK_TYPE_TOOL.equals(command.task().taskType())) {
            return executeToolTask(command);
        }
        if (TASK_TYPE_MODEL.equals(command.task().taskType())) {
            return executeModelTask(command);
        }
        return result(failed(command.task(), "unsupported taskType " + command.task().taskType()));
    }

    private TeamTaskExecutionResultDTO executeToolTask(ExecuteTeamTaskCommand command) {
        TeamTaskDTO task = command.task();
        AgentToolDTO tool = findTool(command, task.suggestedTool());
        if (tool == null) {
            return result(failed(task, "tool " + safe(task.suggestedTool()) + " is not authorized"));
        }
        try {
            AgentToolDispatchResult dispatchResult = toolDispatcher.dispatch(new AgentToolDispatchCommand(
                    command.tenantId(),
                    command.userId(),
                    tool.name(),
                    tool.sourceType(),
                    task.arguments()
            ));
            if (dispatchResult.success()) {
                return new TeamTaskExecutionResultDTO(
                        new ExecutionResultDTO(
                                task.id(),
                                task.taskType(),
                                STATUS_SUCCESS,
                                outputText(dispatchResult.output()),
                                List.of(tool.name()),
                                null
                        ),
                        List.of(),
                        List.of(dispatchResult)
                );
            }
            return new TeamTaskExecutionResultDTO(
                    failed(task, safe(dispatchResult.errorMessage())),
                    List.of(),
                    List.of(dispatchResult)
            );
        } catch (Exception ex) {
            return result(failed(task, errorMessage(ex)));
        }
    }

    private TeamTaskExecutionResultDTO executeModelTask(ExecuteTeamTaskCommand command) {
        TeamTaskDTO task = command.task();
        try {
            ModelInvokeResult modelResult = modelInvokeService.invoke(new ModelInvokeCommand(
                    command.context().modelConfigId(),
                    modelMessages(command),
                    MODEL_TASK_TEMPERATURE,
                    false
            ));
            String content = modelResult == null ? "" : safe(modelResult.assistantMessage());
            return new TeamTaskExecutionResultDTO(
                    new ExecutionResultDTO(
                            task.id(),
                            task.taskType(),
                            STATUS_SUCCESS,
                            content,
                            List.of(),
                            null
                    ),
                    modelResult == null ? List.of() : List.of(modelResult),
                    List.of()
            );
        } catch (Exception ex) {
            return result(failed(task, errorMessage(ex)));
        }
    }

    private List<ModelMessage> modelMessages(ExecuteTeamTaskCommand command) {
        String prompt = """
                Original user request:
                %s

                Profile prompt:
                %s

                Current task:
                id: %s
                name: %s
                description: %s

                Completed task results:
                %s

                Rules:
                - Execute only the current task.
                - Do not re-plan.
                - Do not call tools.
                - Do not generate the final answer or overall summary.
                - Return only the current task result.
                """.formatted(
                safe(command.userInput()),
                profilePrompt(command),
                safe(command.task().id()),
                safe(command.task().name()),
                safe(command.task().description()),
                previousResultSummary(command.previousResults())
        );
        return List.of(
                new ModelMessage("system", "You are the Executor in an Agent Team. Complete only the assigned MODEL_TASK."),
                new ModelMessage("user", prompt)
        );
    }

    private Optional<ExecutionResultDTO> firstDependencyFailure(ExecuteTeamTaskCommand command) {
        Map<String, ExecutionResultDTO> resultByTaskId = command.previousResults().stream()
                .filter(result -> result.taskId() != null)
                .collect(Collectors.toMap(ExecutionResultDTO::taskId, Function.identity(), (first, second) -> first));
        for (String dependency : command.task().dependsOn()) {
            ExecutionResultDTO result = resultByTaskId.get(dependency);
            if (result == null || !STATUS_SUCCESS.equals(result.status())) {
                return Optional.ofNullable(result)
                        .or(() -> Optional.of(new ExecutionResultDTO(dependency, null, STATUS_SKIPPED, "", List.of(), "missing dependency result")));
            }
        }
        return Optional.empty();
    }

    private AgentToolDTO findTool(ExecuteTeamTaskCommand command, String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return null;
        }
        String normalized = toolName.trim();
        return command.availableTools().stream()
                .filter(tool -> normalized.equals(tool.name()))
                .findFirst()
                .orElse(null);
    }

    private String previousResultSummary(List<ExecutionResultDTO> previousResults) {
        if (previousResults == null || previousResults.isEmpty()) {
            return "(none)";
        }
        return previousResults.stream()
                .map(result -> "- " + safe(result.taskId()) + " [" + safe(result.status()) + "]: " + safe(result.result()))
                .collect(Collectors.joining("\n"));
    }

    private String profilePrompt(ExecuteTeamTaskCommand command) {
        ProfileDTO profile = command.context().profile();
        if (profile == null || profile.promptExtra() == null || profile.promptExtra().isBlank()) {
            return "(none)";
        }
        return profile.promptExtra().strip();
    }

    private TeamTaskExecutionResultDTO result(ExecutionResultDTO result) {
        return new TeamTaskExecutionResultDTO(result, List.of(), List.of());
    }

    private ExecutionResultDTO failed(TeamTaskDTO task, String message) {
        return new ExecutionResultDTO(task.id(), task.taskType(), STATUS_FAILED, "", List.of(), message);
    }

    private ExecutionResultDTO skipped(TeamTaskDTO task, String message) {
        return new ExecutionResultDTO(task.id(), task.taskType(), STATUS_SKIPPED, "", List.of(), message);
    }

    private String outputText(JsonNode output) {
        return output == null ? "" : output.toString();
    }

    private void validate(ExecuteTeamTaskCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "execute team task command is required");
        }
        if (command.task() == null) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "team task is required");
        }
        if (command.context() == null) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "agent context is required");
        }
        if (command.context().modelConfigId() == null) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "modelConfigId is required");
        }
    }

    private String errorMessage(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
