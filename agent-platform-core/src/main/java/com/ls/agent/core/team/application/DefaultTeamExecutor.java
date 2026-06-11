package com.ls.agent.core.team.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import com.ls.agent.core.model.dto.ModelToolCallDTO;
import com.ls.agent.core.model.dto.ModelToolSpecDTO;
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
    private static final String TEAM_SOURCE = "TEAM";
    private static final String MODEL_TASK_RESULT_TOOL_NAME = "team_model_task_result";
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
                    false,
                    List.of(modelTaskResultTool(command.task()))
            ));
            ExecutionResultDTO executionResult = executionResultFromFunctionCall(command.task(), modelResult);
            if (executionResult == null) {
                String content = modelResult == null ? "" : safe(modelResult.assistantMessage());
                executionResult = new ExecutionResultDTO(
                        task.id(),
                        task.taskType(),
                        STATUS_SUCCESS,
                        content,
                        List.of(),
                        null
                );
            }
            return new TeamTaskExecutionResultDTO(
                    executionResult,
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

    private ModelToolSpecDTO modelTaskResultTool(TeamTaskDTO task) {
        return new ModelToolSpecDTO(
                TEAM_SOURCE,
                MODEL_TASK_RESULT_TOOL_NAME,
                "Return the structured result for the assigned MODEL_TASK. Do not call business tools.",
                modelTaskResultSchema(task)
        );
    }

    private JsonNode modelTaskResultSchema(TeamTaskDTO task) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("type", "object");
        ObjectNode properties = root.putObject("properties");
        properties.putObject("taskId").put("type", "string").put("description", "Must equal " + safe(task.id()));
        properties.putObject("taskType").put("type", "string").putArray("enum").add(TASK_TYPE_MODEL);
        properties.putObject("status").put("type", "string").putArray("enum").add(STATUS_SUCCESS).add(STATUS_FAILED);
        properties.putObject("result").put("type", "string").put("description", "Result for the assigned task only.");
        ObjectNode usedTools = properties.putObject("usedTools");
        usedTools.put("type", "array");
        usedTools.putObject("items").put("type", "string");
        ObjectNode errorMessage = properties.putObject("errorMessage");
        errorMessage.putArray("type").add("string").add("null");
        root.putArray("required").add("taskId").add("taskType").add("status").add("result").add("usedTools");
        return root;
    }

    private ExecutionResultDTO executionResultFromFunctionCall(TeamTaskDTO task, ModelInvokeResult modelResult) {
        JsonNode arguments = firstToolArguments(modelResult, MODEL_TASK_RESULT_TOOL_NAME);
        if (arguments == null) {
            return null;
        }
        String status = safe(arguments.path("status").asText(STATUS_SUCCESS)).isBlank()
                ? STATUS_SUCCESS
                : arguments.path("status").asText(STATUS_SUCCESS).strip();
        String errorMessage = arguments.path("errorMessage").isMissingNode() || arguments.path("errorMessage").isNull()
                ? null
                : arguments.path("errorMessage").asText();
        return new ExecutionResultDTO(
                task.id(),
                task.taskType(),
                status,
                arguments.path("result").asText(""),
                List.of(),
                errorMessage
        );
    }

    private JsonNode firstToolArguments(ModelInvokeResult result, String toolName) {
        if (result == null || result.toolCalls() == null) {
            return null;
        }
        for (ModelToolCallDTO call : result.toolCalls()) {
            if (call != null
                    && TEAM_SOURCE.equalsIgnoreCase(call.sourceType())
                    && toolName.equals(call.name())) {
                return call.arguments();
            }
        }
        return null;
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
