package com.study.interview.controller;

import com.study.interview.common.Result;
import com.study.interview.config.RateLimit;
import com.study.interview.exception.BizException;
import com.study.interview.model.InterviewAnswerRequest;
import com.study.interview.model.InterviewAnswerResponse;
import com.study.interview.model.InterviewStartRequest;
import com.study.interview.model.InterviewStartResponse;
import com.study.interview.service.InterviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/interview")
public class InterviewController {

    private static final Logger log = LoggerFactory.getLogger(InterviewController.class);

    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    // 开始一场模拟面试：简历全文进模型，成本与简历评审同级，配额收紧到 60 秒 3 次
    @RateLimit(limit = 3, windowSeconds = 60)
    @PostMapping(value = "/start", params = "stream!=true")
    public Result<InterviewStartResponse> start(@RequestBody InterviewStartRequest request) {
        // Controller 只做协议搬运：取参、调 Service、包统一信封；校验和业务都在 Service 里
        return Result.success(interviewService.start(request.getResumeText(), request.getTargetPosition()));
    }

    // 答题由会话状态和轮次校验约束，允许连续完成多个轮次。
    @PostMapping(value = "/{sessionId}/answer", params = "stream!=true")
    public Result<InterviewAnswerResponse> answer(@PathVariable Long sessionId,
                                                  @RequestBody InterviewAnswerRequest request) {
        // Controller 只做协议搬运：取参、调 Service、包统一信封；校验和业务都在 Service 里
        return Result.success(interviewService.answer(sessionId, request.getRoundNo(), request.getAnswerText()));
    }

    @RateLimit(limit = 3, windowSeconds = 60)
    @PostMapping(value = "/start", params = "stream=true", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> startStream(@RequestBody InterviewStartRequest request) {
        return toSse(interviewService.streamStart(request.getResumeText(), request.getTargetPosition()));
    }

    @PostMapping(value = "/{sessionId}/answer", params = "stream=true", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> answerStream(@PathVariable Long sessionId,
                                                      @RequestBody InterviewAnswerRequest request) {
        return toSse(interviewService.streamAnswer(sessionId, request.getRoundNo(), request.getAnswerText()));
    }

    private Flux<ServerSentEvent<Object>> toSse(Flux<InterviewService.StreamEvent> events) {
        return events.map(event -> sse(event.type(), event.data()))
                .onErrorResume(BizException.class, error ->
                        Mono.just(sse("problem", Result.error(error.getCode(), error.getMessage()))))
                .onErrorResume(error -> {
                    log.error("Interview stream failed", error);
                    return Mono.just(sse("problem", Result.error(500, "这次没能完成请求，请稍后再试")));
                });
    }

    private ServerSentEvent<Object> sse(String event, Object data) {
        return ServerSentEvent.builder(data).event(event).build();
    }
}
