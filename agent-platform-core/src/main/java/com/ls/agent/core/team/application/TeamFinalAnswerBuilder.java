package com.ls.agent.core.team.application;

import com.ls.agent.core.team.dto.ReviewResultDTO;
import org.springframework.stereotype.Component;

@Component
public class TeamFinalAnswerBuilder {

    public String build(String answerDraft, ReviewResultDTO review) {
        String draft = answerDraft == null ? "" : answerDraft.strip();
        if (review == null || review.issues().isEmpty()) {
            return draft;
        }
        StringBuilder builder = new StringBuilder(draft);
        if (!builder.isEmpty()) {
            builder.append("\n\n");
        }
        builder.append("Review notes: ").append(review.summary() == null ? "" : review.summary());
        return builder.toString();
    }
}
