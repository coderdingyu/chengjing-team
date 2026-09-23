package com.chengjing.assessment.scoring;

import com.chengjing.shared.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assessments/answers")
public class EvidenceScoringController {
    private final CurrentUserPort currentUserPort;
    private final InterviewAnswerPort answerPort;
    private final EvidenceScoringService scoringService;

    public EvidenceScoringController(
            CurrentUserPort currentUserPort,
            InterviewAnswerPort answerPort,
            EvidenceScoringService scoringService
    ) {
        this.currentUserPort = currentUserPort;
        this.answerPort = answerPort;
        this.scoringService = scoringService;
    }

    @PostMapping("/{answerId}/score")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EvidenceScore> score(@PathVariable String answerId) {
        String userId = currentUserPort.requireUserId();
        AnswerSnapshot answer = answerPort.requireOwnedAnswer(userId, answerId);
        return ApiResponse.ok(scoringService.score(answer));
    }
}
