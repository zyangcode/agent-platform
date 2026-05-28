package com.ls.agent.core.team.api;

import com.ls.agent.core.team.command.ReviewTeamCommand;
import com.ls.agent.core.team.dto.TeamReviewResultDTO;

public interface TeamReviewer {

    TeamReviewResultDTO review(ReviewTeamCommand command);
}
