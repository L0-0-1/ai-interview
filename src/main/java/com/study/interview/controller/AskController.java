package com.study.interview.controller;

import com.study.interview.common.Result;
import com.study.interview.config.RateLimit;
import com.study.interview.model.Question;
import com.study.interview.service.AskService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ask")
public class AskController {
    private final AskService askService;
    public AskController(AskService askService) {
        this.askService = askService;
    }
    // 简单问答成本较低（只检索 + 一次对话），配额放宽到 60 秒 5 次
    @RateLimit(limit = 1, windowSeconds = 60)
    @PostMapping("/question")
    public Result<String> askQuestion(@RequestBody Question question) {
        return Result.success(askService.ask(question.getQuestion()));
    }
}
