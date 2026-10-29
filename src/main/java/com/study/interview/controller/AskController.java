package com.study.interview.controller;

import com.study.interview.common.Result;
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
    @PostMapping("/question")
    public Result<String> askQuestion(@RequestBody Question question) {
        return Result.success(askService.ask(question.getQuestion()));
    }
}
