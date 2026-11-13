package com.study.interview;

import com.study.interview.config.RateLimitInterceptor;
import com.study.interview.config.SecurityConfig;
import com.study.interview.controller.InterviewController;
import com.study.interview.exception.BizException;
import com.study.interview.exception.GlobalExceptionHandler;
import com.study.interview.model.InterviewSession;
import com.study.interview.service.InterviewService;
import com.study.interview.service.InterviewSessionService;
import com.study.interview.service.JwtTokenService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(InterviewStreamTests.WebConfig.class)
class InterviewStreamTests {

    @Autowired private ChatClient chatClient;
    @Autowired private InterviewSessionService sessions;
    @Autowired private StringRedisTemplate redis;
    @Autowired private JwtTokenService tokenService;
    @Autowired private WebApplicationContext context;
    @Autowired @Qualifier("springSecurityFilterChain") private Filter securityFilter;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(chatClient, sessions, redis);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
    }

    @Test
    void streamsFirstQuestionThenConfirmsSaveAndCountsRequestOnce() throws Exception {
        modelChunks(Flux.just("你如何", "分页？"));
        when(sessions.createSessionAndFirstMessage(1L, "项目经历", "后端", "你如何分页？")).thenReturn(7L);

        String body = stream("/api/interview/start", """
                {"resumeText":"项目经历","targetPosition":"后端"}
                """);

        assertThat(body).containsSubsequence("event:delta", "你如何", "event:delta", "分页？", "event:done", "你如何分页？");
        assertThat(body).contains("\"sessionId\":7", "\"currentRound\":1");
        verify(sessions).createSessionAndFirstMessage(1L, "项目经历", "后端", "你如何分页？");
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
        when(sessions.advanceToNextRound(7L, 1L, 1, "按主键排序", "有新数据插入时呢？")).thenReturn(2);
        modelChunks(Flux.just("有新数据", "插入时呢？"));

        String body = stream("/api/interview/7/answer", """
                {"roundNo":1,"answerText":"按主键排序"}
                """);

        assertThat(body).containsSubsequence("event:delta", "有新数据", "event:delta", "插入时呢？", "event:done");
        assertThat(body).contains("\"status\":\"ONGOING\"", "\"currentRound\":2");
        verify(sessions).advanceToNextRound(7L, 1L, 1, "按主键排序", "有新数据插入时呢？");
        verifyNoInteractions(redis);
    }

    @Test
    void endsAfterFinalAnswerWithoutAskingTheModel() throws Exception {
        setCurrentRound(8);

        String body = stream("/api/interview/7/answer", """
                {"roundNo":8,"answerText":"最后一个回答"}
                """);

        assertThat(body).contains("event:done", "\"status\":\"ENDED\"", "\"currentRound\":8").doesNotContain("event:delta");
        verify(sessions).endSessionWithLastAnswer(7L, 1L, 8, "最后一个回答");
        verifyNoInteractions(chatClient);
    }

    @Test
    void jsonAnswerAlsoSkipsRequestRateLimit() throws Exception {
        setCurrentRound(8);

        mvc.perform(authenticatedPost("/api/interview/7/answer", 1L).content("""
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
        when(sessions.createSessionAndFirstMessage(1L, "项目经历", "后端", "你如何分页？")).thenReturn(7L);

        mvc.perform(authenticatedPost("/api/interview/start", 1L).content("""
                        {"resumeText":"项目经历","targetPosition":"后端"}
                        """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sessionId").value(7));
    }

    @Test
    void rejectsInterviewPostWithoutToken() throws Exception {
        mvc.perform(post("/api/interview/start").queryParam("stream", "true")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(chatClient, sessions, redis);
    }

    @Test
    void anotherAccountCannotAnswerTheSessionThroughEitherEndpoint() throws Exception {
        when(sessions.getOwnedSessionOrThrow(7L, 2L))
                .thenThrow(new BizException(1002, HttpStatus.NOT_FOUND, "面试会话不存在"));
        String answer = """
                {"roundNo":1,"answerText":"按主键排序","userId":1}
                """;

        mvc.perform(authenticatedPost("/api/interview/7/answer", 2L).content(answer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1002));

        String body = stream(streamRequest("/api/interview/7/answer", answer, 2L));
        assertThat(body).contains("event:problem", "\"code\":1002").doesNotContain("event:delta", "event:done");
        verifyNoInteractions(chatClient, redis);
    }

    private String stream(String path, String body) throws Exception {
        return stream(streamRequest(path, body));
    }

    private String stream(MockHttpServletRequestBuilder requestBuilder) throws Exception {
        MvcResult initial = mvc.perform(requestBuilder)
                .andExpect(request().asyncStarted())
                .andReturn();
        return mvc.perform(asyncDispatch(initial))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private MockHttpServletRequestBuilder streamRequest(String path, String body) {
        return streamRequest(path, body, 1L);
    }

    private MockHttpServletRequestBuilder streamRequest(String path, String body, Long userId) {
        return authenticatedPost(path, userId).queryParam("stream", "true")
                .accept("text/event-stream, application/json;q=0.9").content(body);
    }

    private MockHttpServletRequestBuilder authenticatedPost(String path, Long userId) {
        return post(path).header("Authorization", "Bearer " + tokenService.issue(userId))
                .contentType(MediaType.APPLICATION_JSON);
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
        when(sessions.getOwnedSessionOrThrow(7L, 1L)).thenReturn(session);
    }

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, InterviewController.class, InterviewService.class, GlobalExceptionHandler.class})
    static class WebConfig implements WebMvcConfigurer {

        @Bean
        ChatClient chatClient() {
            return mock(ChatClient.class);
        }

        @Bean
        InterviewSessionService sessions() {
            return mock(InterviewSessionService.class);
        }

        @Bean
        StringRedisTemplate redis() {
            return mock(StringRedisTemplate.class);
        }

        @Bean
        JwtTokenService tokenService() {
            byte[] key = new byte[32];
            new SecureRandom().nextBytes(key);
            return new JwtTokenService(Base64.getEncoder().encodeToString(key));
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new RateLimitInterceptor(redis()));
        }
    }
}
