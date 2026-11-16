package com.study.interview.controller;

import com.study.interview.common.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.study.interview.config.RateLimit;
import com.study.interview.controller.dto.InterviewDetail;
import com.study.interview.controller.dto.InterviewHistoryItem;
import com.study.interview.exception.BizException;
import com.study.interview.model.InterviewAnswerRequest;
import com.study.interview.model.InterviewAnswerResponse;
import com.study.interview.model.InterviewStartRequest;
import com.study.interview.model.InterviewStartResponse;
import com.study.interview.service.InterviewService;
import com.study.interview.service.InterviewSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/interview")
public class InterviewController {

    private static final Logger log = LoggerFactory.getLogger(InterviewController.class);

    private final InterviewService interviewService;
    private final InterviewSessionService sessionService;

    public InterviewController(InterviewService interviewService, InterviewSessionService sessionService) {
        this.interviewService = interviewService;
        this.sessionService = sessionService;
    }

    @GetMapping("/history")
    public Result<Page<InterviewHistoryItem>> history(@AuthenticationPrincipal Jwt jwt,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "10") int size) {
        return Result.success(sessionService.history(Long.valueOf(jwt.getSubject()), page, size));
    }

    @GetMapping("/{sessionId}")
    public Result<InterviewDetail> detail(@PathVariable Long sessionId, @AuthenticationPrincipal Jwt jwt) {
        return Result.success(sessionService.detail(sessionId, Long.valueOf(jwt.getSubject())));
    }

    // 开始一场模拟面试：简历全文进模型，成本与简历评审同级，配额收紧到 60 秒 3 次
    @RateLimit(limit = 3, windowSeconds = 60)
    @PostMapping(value = "/start", params = "stream!=true")
    public Result<InterviewStartResponse> start(@AuthenticationPrincipal Jwt jwt,
                                                @RequestBody InterviewStartRequest request) {
        // Controller 只做协议搬运：取参、调 Service、包统一信封；校验和业务都在 Service 里
        return Result.success(interviewService.start(Long.valueOf(jwt.getSubject()),
                request.getResumeText(), request.getTargetPosition()));
    }

    // 答题由会话状态和轮次校验约束，允许连续完成多个轮次。
    @PostMapping(value = "/{sessionId}/answer", params = "stream!=true")
    public Result<InterviewAnswerResponse> answer(@PathVariable Long sessionId,
                                                  @AuthenticationPrincipal Jwt jwt,
                                                  @RequestBody InterviewAnswerRequest request) {
        // Controller 只做协议搬运：取参、调 Service、包统一信封；校验和业务都在 Service 里
        return Result.success(interviewService.answer(sessionId, Long.valueOf(jwt.getSubject()),
                request.getRoundNo(), request.getAnswerText()));
    }

    @RateLimit(limit = 3, windowSeconds = 60)
    @PostMapping(value = "/start", params = "stream=true", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> startStream(@AuthenticationPrincipal Jwt jwt,
                                                      @RequestBody InterviewStartRequest request) {
        return toSse(interviewService.streamStart(Long.valueOf(jwt.getSubject()),
                request.getResumeText(), request.getTargetPosition()));
    }

    @PostMapping(value = "/{sessionId}/answer", params = "stream=true", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> answerStream(@PathVariable Long sessionId,
                                                      @AuthenticationPrincipal Jwt jwt,
                                                      @RequestBody InterviewAnswerRequest request) {
        return toSse(interviewService.streamAnswer(sessionId, Long.valueOf(jwt.getSubject()),
                request.getRoundNo(), request.getAnswerText()));
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
