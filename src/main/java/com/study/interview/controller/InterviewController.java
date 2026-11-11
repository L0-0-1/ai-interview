package com.study.interview.controller;

import com.study.interview.common.Result;
import com.study.interview.config.RateLimit;
import com.study.interview.model.InterviewAnswerRequest;
import com.study.interview.model.InterviewAnswerResponse;
import com.study.interview.model.InterviewStartRequest;
import com.study.interview.model.InterviewStartResponse;
import com.study.interview.service.InterviewService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interview")
public class InterviewController {

    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    // 开始一场模拟面试：简历全文进模型，成本与简历评审同级，配额收紧到 60 秒 3 次
    @RateLimit(limit = 3, windowSeconds = 60)
    @PostMapping("/start")
    public Result<InterviewStartResponse> start(@RequestBody InterviewStartRequest request) {
        // Controller 只做协议搬运：取参、调 Service、包统一信封；校验和业务都在 Service 里
        return Result.success(interviewService.start(request.getResumeText(), request.getTargetPosition()));
    }

    // 提交当前轮回答并推进下一题：题目 + 回答进模型，成本与开始面试同级，配额同样收紧到 60 秒 3 次
    @RateLimit(limit = 3, windowSeconds = 60)
    @PostMapping("/{sessionId}/answer")
    public Result<InterviewAnswerResponse> answer(@PathVariable Long sessionId,
                                                  @RequestBody InterviewAnswerRequest request) {
        // Controller 只做协议搬运：取参、调 Service、包统一信封；校验和业务都在 Service 里
        return Result.success(interviewService.answer(sessionId, request.getRoundNo(), request.getAnswerText()));
    }
}
