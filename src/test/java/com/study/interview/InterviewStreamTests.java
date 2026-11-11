package com.study.interview;

import com.study.interview.config.RateLimitInterceptor;
import com.study.interview.controller.InterviewController;
import com.study.interview.exception.GlobalExceptionHandler;
import com.study.interview.model.InterviewSession;
import com.study.interview.service.InterviewService;
import com.study.interview.service.InterviewSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InterviewStreamTests {

    private final ChatClient chatClient = mock(ChatClient.class);
    private final InterviewSessionService sessions = mock(InterviewSessionService.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new InterviewController(new InterviewService(chatClient, sessions)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new RateLimitInterceptor(redis))
                .build();
    }

    @Test
    void streamsFirstQuestionThenConfirmsSaveAndCountsRequestOnce() throws Exception {
        modelChunks(Flux.just("你如何", "分页？"));
        when(sessions.createSessionAndFirstMessage("项目经历", "后端", "你如何分页？")).thenReturn(7L);

        String body = stream("/api/interview/start", """
                {"resumeText":"项目经历","targetPosition":"后端"}
                """);

        assertThat(body).containsSubsequence("event:delta", "你如何", "event:delta", "分页？", "event:done", "你如何分页？");
        assertThat(body).contains("\"sessionId\":7", "\"currentRound\":1");
        verify(sessions).createSessionAndFirstMessage("项目经历", "后端", "你如何分页？");
        verify(redis).execute(any(RedisScript.class), eq(List.of("rate:/api/interview/start:127.0.0.1")), eq("60"));
    }

    @Test
    void failedStreamDoesNotSavePartialQuestion() throws Exception {
        modelChunks(Flux.just("半道题").concatWith(Flux.error(new IllegalStateException("test model failure"))));

        String body = stream("/api/interview/start", """
                {"resumeText":"项目经历","targetPosition":"后端"}
                """);

        assertThat(body).containsSubsequence("event:delta", "半道题", "event:problem").doesNotContain("event:done");
        verifyNoInteractions(sessions);
    }

    @Test
    void streamsFollowUpAndAdvancesOnlyOnce() throws Exception {
        setCurrentRound(1);
        when(sessions.getCurrentQuestionOrThrow(7L, 1)).thenReturn("如何分页？");
        when(sessions.advanceToNextRound(7L, 1, "按主键排序", "有新数据插入时呢？")).thenReturn(2);
        modelChunks(Flux.just("有新数据", "插入时呢？"));

        String body = stream("/api/interview/7/answer", """
                {"roundNo":1,"answerText":"按主键排序"}
                """);

        assertThat(body).containsSubsequence("event:delta", "有新数据", "event:delta", "插入时呢？", "event:done");
        assertThat(body).contains("\"status\":\"ONGOING\"", "\"currentRound\":2");
        verify(sessions).advanceToNextRound(7L, 1, "按主键排序", "有新数据插入时呢？");
        verifyNoInteractions(redis);
    }

    @Test
    void endsAfterFinalAnswerWithoutAskingTheModel() throws Exception {
        setCurrentRound(8);

        String body = stream("/api/interview/7/answer", """
                {"roundNo":8,"answerText":"最后一个回答"}
                """);

        assertThat(body).contains("event:done", "\"status\":\"ENDED\"", "\"currentRound\":8").doesNotContain("event:delta");
        verify(sessions).endSessionWithLastAnswer(7L, 8, "最后一个回答");
        verifyNoInteractions(chatClient);
    }

    @Test
    void jsonAnswerAlsoSkipsRequestRateLimit() throws Exception {
        setCurrentRound(8);

        mvc.perform(post("/api/interview/7/answer").contentType(MediaType.APPLICATION_JSON).content("""
                        {"roundNo":8,"answerText":"最后一个回答"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENDED"));
        verifyNoInteractions(redis);
    }

    @Test
    void rateLimitedStreamStillReturnsAnHttpError() throws Exception {
        when(redis.execute(any(RedisScript.class), eq(List.of("rate:/api/interview/start:127.0.0.1")), eq("60")))
                .thenReturn(4L);

        mvc.perform(streamRequest("/api/interview/start", "{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        verifyNoInteractions(chatClient, sessions);
    }

    @Test
    void existingClientStillReceivesTheJsonEnvelope() throws Exception {
        ChatClient.ChatClientRequestSpec prompt = modelPrompt();
        ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
        when(prompt.call()).thenReturn(response);
        when(response.content()).thenReturn("你如何分页？");
        when(sessions.createSessionAndFirstMessage("项目经历", "后端", "你如何分页？")).thenReturn(7L);

        mvc.perform(post("/api/interview/start").contentType(MediaType.APPLICATION_JSON).content("""
                        {"resumeText":"项目经历","targetPosition":"后端"}
                        """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sessionId").value(7));
    }

    private String stream(String path, String body) throws Exception {
        MvcResult initial = mvc.perform(streamRequest(path, body))
                .andExpect(request().asyncStarted())
                .andReturn();
        return mvc.perform(asyncDispatch(initial))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private MockHttpServletRequestBuilder streamRequest(String path, String body) {
        return post(path).queryParam("stream", "true").contentType(MediaType.APPLICATION_JSON)
                .accept("text/event-stream, application/json;q=0.9").content(body);
    }

    private void modelChunks(Flux<String> chunks) {
        ChatClient.ChatClientRequestSpec prompt = modelPrompt();
        ChatClient.StreamResponseSpec stream = mock(ChatClient.StreamResponseSpec.class);
        when(prompt.stream()).thenReturn(stream);
        when(stream.content()).thenReturn(chunks);
    }

    private ChatClient.ChatClientRequestSpec modelPrompt() {
        ChatClient.ChatClientRequestSpec prompt = mock(ChatClient.ChatClientRequestSpec.class);
        when(chatClient.prompt()).thenReturn(prompt);
        when(prompt.system(anyString())).thenReturn(prompt);
        when(prompt.user(anyString())).thenReturn(prompt);
        return prompt;
    }

    private void setCurrentRound(int round) {
        InterviewSession session = new InterviewSession();
        session.setStatus(InterviewSession.STATUS_ONGOING);
        session.setCurrentRound(round);
        session.setMaxRound(8);
        session.setTargetPosition("后端");
        when(sessions.getSessionOrThrow(7L)).thenReturn(session);
    }
}
