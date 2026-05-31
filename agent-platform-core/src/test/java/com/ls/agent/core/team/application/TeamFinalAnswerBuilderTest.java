package com.ls.agent.core.team.application;

import com.ls.agent.core.team.dto.ReviewResultDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TeamFinalAnswerBuilderTest {

    private final TeamFinalAnswerBuilder builder = new TeamFinalAnswerBuilder();

    @Test
    void doesNotExposeInternalDraftLabelsInFinalAnswer() {
        String answer = builder.build("""
                User request: 你好
                Goal: Respond to user greeting
                Execution results:
                - task-1 [SUCCESS]: 你好！有什么可以帮你的吗？
                """, new ReviewResultDTO(true, List.of(), List.of(), "review passed"));

        assertThat(answer).isEqualTo("你好！有什么可以帮你的吗？");
        assertThat(answer).doesNotContain("User request:", "Goal:", "Execution results:", "task-1");
    }

    @Test
    void doesNotExposeChineseInternalDraftLabelsInFinalAnswer() {
        String answer = builder.build("""
                用户请求：你好
                目标：回复用户问候
                执行结果：
                - task-1 [SUCCESS]: 你好！有什么可以帮你的吗？
                """, new ReviewResultDTO(true, List.of(), List.of(), "审查通过"));

        assertThat(answer).isEqualTo("你好！有什么可以帮你的吗？");
        assertThat(answer).doesNotContain("用户请求：", "目标：", "执行结果：", "task-1");
    }

    @Test
    void prefersModelAnswerAndDoesNotExposeToolJsonOrRawReviewSummary() {
        String answer = builder.build("""
                用户请求：我要组织团建，20人，重庆
                目标：制定重庆 20 人团建计划
                执行结果：
                - task-search [SUCCESS]: {"results":[{"title":"Mock search result for 重庆","url":"https://example.com/search"}]}
                - task-final [SUCCESS]: {"answer":"建议安排重庆室内轻量团建：下午先做分组破冰，再进行桌游或密室协作，晚上聚餐。20 人可分 4 组，每组 5 人，预算按人均 150-250 元预估。"}
                """, new ReviewResultDTO(
                false,
                List.of(new ReviewResultDTO.ReviewIssueDTO("task-search", "WARN", "工具结果是 mock 数据")),
                List.of(),
                "Task-1 succeeded with mock search result; task-2 failed due to missing city argument."
        ));

        assertThat(answer).isEqualTo("建议安排重庆室内轻量团建：下午先做分组破冰，再进行桌游或密室协作，晚上聚餐。20 人可分 4 组，每组 5 人，预算按人均 150-250 元预估。");
        assertThat(answer).doesNotContain("Mock search result", "\"results\"", "\"answer\"", "Task-1", "missing city");
    }

    @Test
    void extractsAnswerFromPrettyPrintedJsonTaskResult() {
        String answer = builder.build("""
                用户请求：我要组织团建，20人，重庆
                目标：制定重庆 20 人团建计划
                执行结果：
                - task-final [SUCCESS]: {
                  "answer": "建议安排重庆室内轻量团建：下午破冰分组，随后进行桌游或密室协作，晚上聚餐。20 人分 4 组，每组 5 人。"
                }
                """, new ReviewResultDTO(true, List.of(), List.of(), "审查通过"));

        assertThat(answer).isEqualTo("建议安排重庆室内轻量团建：下午破冰分组，随后进行桌游或密室协作，晚上聚餐。20 人分 4 组，每组 5 人。");
        assertThat(answer).doesNotContain("{", "\"answer\"", "}");
    }

    @Test
    void keepsMarkdownBulletListFromSuccessfulTaskResult() {
        String answer = builder.build("""
                User request: organize team activity for 20 people
                Goal: make a practical activity plan
                Execution results:
                - task-3 [SUCCESS]:
                  - Morning: check in, icebreaker, split into four groups.
                  - Afternoon: team challenge, points competition, backup indoor option.
                  - Evening: dinner, awards, short retrospective.
                """, new ReviewResultDTO(true, List.of(), List.of(), "review passed"));

        assertThat(answer).contains(
                "Morning: check in, icebreaker, split into four groups.",
                "Afternoon: team challenge, points competition, backup indoor option.",
                "Evening: dinner, awards, short retrospective."
        );
        assertThat(answer).doesNotContain("User request:", "Execution results:", "task-3");
    }

    @Test
    void doesNotFallBackToInternalDraftWhenOnlyMockToolJsonAndFailuresExist() {
        String answer = builder.build("""
                用户请求：我要组织团建，20人，给我计划
                重庆
                目标：为20人重庆团建活动制定计划
                执行结果：
                - task-1 [SUCCESS]: {"results":[{"title":"Mock search result for 重庆20人团建活动推荐","url":"https://example.com/search"}]}
                - task-2 [FAILED]:  error=Missing skill argument: city
                - task-3 [SKIPPED]:  error=dependsOn task task-2 is not successful
                """, new ReviewResultDTO(
                false,
                List.of(new ReviewResultDTO.ReviewIssueDTO("task-2", "ERROR", "Missing city argument")),
                List.of(),
                "Need a usable final answer instead of leaking execution details."
        ));

        // Empty answer signals that no usable result was found; the Orchestrator's
        // fallbackModelAnswer will take over with a direct model call.
        assertThat(answer).isEmpty();
    }
}
