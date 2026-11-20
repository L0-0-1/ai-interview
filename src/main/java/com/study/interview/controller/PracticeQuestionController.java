package com.study.interview.controller;

import com.study.interview.common.Result;
import com.study.interview.config.RateLimit;
import com.study.interview.controller.dto.PracticeFeedback;
import com.study.interview.controller.dto.PracticeQuestionView;
import com.study.interview.service.PracticeQuestionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interview/practice")
public class PracticeQuestionController {
    private final PracticeQuestionService practiceService;

    public PracticeQuestionController(PracticeQuestionService practiceService) {
        this.practiceService = practiceService;
    }

    @GetMapping("/{questionId}")
    public Result<PracticeQuestionView> question(@PathVariable Long questionId) {
        return Result.success(practiceService.question(questionId));
    }

    @RateLimit(limit = 3, windowSeconds = 60)
    @PostMapping("/{questionId}/answer")
    public Result<PracticeFeedback> answer(@PathVariable Long questionId, @RequestBody AnswerRequest request) {
        return Result.success(practiceService.answer(questionId, request.answerText()));
    }

    public record AnswerRequest(String answerText) {
        @Override
        public String toString() {
            return "AnswerRequest";
        }
    }
}
