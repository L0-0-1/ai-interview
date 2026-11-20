package com.study.interview;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.interview.controller.dto.InterviewDetail;
import com.study.interview.controller.dto.InterviewSessionSummary;
import com.study.interview.config.RateLimitInterceptor;
import com.study.interview.config.SecurityConfig;
import com.study.interview.controller.AskController;
import com.study.interview.controller.InterviewController;
import com.study.interview.controller.PracticeQuestionController;
import com.study.interview.exception.BizException;
import com.study.interview.exception.GlobalExceptionHandler;
import com.study.interview.mapper.PracticeQuestionMapper;
import com.study.interview.model.InterviewSession;
import com.study.interview.service.AskService;
import com.study.interview.service.InterviewReviewService;
import com.study.interview.service.InterviewService;
import com.study.interview.service.InterviewSessionService;
import com.study.interview.service.JwtTokenService;
import com.study.interview.service.PracticeQuestionService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
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
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(InterviewStreamTests.WebConfig.class)
class InterviewStreamTests {

    @Autowired private ChatClient chatClient;
    @Autowired private InterviewSessionService sessions;
    @Autowired private PracticeQuestionMapper practiceQuestions;
    @Autowired private AskService askService;
    @Autowired private StringRedisTemplate redis;
    @Autowired private JwtTokenService tokenService;
    @Autowired private WebApplicationContext context;
    @Autowired @Qualifier("springSecurityFilterChain") private Filter securityFilter;
    private MockMvc mvc;
    private ValueOperations<String, String> cache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        reset(chatClient, sessions, redis, practiceQuestions, askService);
        cache = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(cache);
        when(cache.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(5)))).thenReturn(true);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
    }

    @Test
    void historyRequiresAuthenticationAndUsesVerifiedUserId() throws Exception {
        mvc.perform(get("/api/interview/history")).andExpect(status().isUnauthorized());
        Page<InterviewSessionSummary> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(new InterviewSessionSummary(7L, "后端", null, "ONGOING", 2)));
        when(sessions.history(1L, 1, 10, null)).thenReturn(page);

        mvc.perform(get("/api/interview/history").header("Authorization", "Bearer " + tokenService.issue(1L))
                        .queryParam("userId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(7))
                .andExpect(jsonPath("$.data.records[0].resumeText").doesNotExist());
        verify(sessions).history(1L, 1, 10, null);
    }

    @Test
    void historyPassesTheRequestedStatusWithTheAuthenticatedAccount() throws Exception {
        Page<InterviewSessionSummary> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(new InterviewSessionSummary(7L, "后端", null, "ENDED", 2)));
        when(sessions.history(1L, 1, 10, "ENDED")).thenReturn(page);

        mvc.perform(get("/api/interview/history")
                        .header("Authorization", "Bearer " + tokenService.issue(1L))
                        .queryParam("status", "ENDED").queryParam("userId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].status").value("ENDED"));
        verify(sessions).history(1L, 1, 10, "ENDED");
    }

    @Test
    void detailRequiresAuthenticationAndUsesVerifiedUserId() throws Exception {
        mvc.perform(get("/api/interview/7")).andExpect(status().isUnauthorized());
        InterviewSessionSummary session = new InterviewSessionSummary(7L, "后端", null, "ONGOING", 1);
        InterviewDetail.Message question = new InterviewDetail.Message(11L, "INTERVIEWER", 1, "你如何做分页？", null);
        when(sessions.detail(7L, 1L)).thenReturn(new InterviewDetail(session, List.of(question)));

        mvc.perform(get("/api/interview/7").header("Authorization", "Bearer " + tokenService.issue(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.id").value(7))
                .andExpect(jsonPath("$.data.session.resumeText").doesNotExist())
                .andExpect(jsonPath("$.data.messages[0].role").value("INTERVIEWER"))
                .andExpect(jsonPath("$.data.messages[0].content").value("你如何做分页？"));
        verify(sessions).detail(7L, 1L);
        verifyNoInteractions(chatClient, redis);
    }

    @Test
    void reviewUsesTheSavedAnswerAndMatchesAPublicQuestion() throws Exception {
        mockReviewSession("ENDED", true);
        mockReviewResponse("""
                {"quote":"加锁","feedback":"提出了加锁思路",
                 "missingPoint":"检查和扣减要原子执行","skillTag":"REDIS_STOCK_ATOMICITY"}
                """);
        String questionText = "假设库存只剩 1 件，怎样保证两个请求只有一个成功？";
        when(practiceQuestions.findFirstBySkillTag("REDIS_STOCK_ATOMICITY"))
                .thenReturn(new PracticeQuestionMapper.Question(1L, questionText));

        mvc.perform(post("/api/interview/7/review").queryParam("roundNo", "2")
                        .header("Authorization", "Bearer " + tokenService.issue(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVIEWED"))
                .andExpect(jsonPath("$.data.answerMessageId").value(12))
                .andExpect(jsonPath("$.data.quote").value("加锁"))
                .andExpect(jsonPath("$.data.practiceQuestionId").value(1))
                .andExpect(jsonPath("$.data.practiceQuestion").value(questionText));
        verify(sessions).detail(7L, 1L);
    }

    @Test
    void reviewKeepsFeedbackWhenARecognizedTagHasNoQuestion() throws Exception {
        mockReviewSession("ENDED", true);
        mockReviewResponse("""
                {"quote":"加锁","feedback":"提出了加锁思路",
                 "missingPoint":"检查和扣减要原子执行","skillTag":"REDIS_STOCK_ATOMICITY"}
                """);

        mvc.perform(post("/api/interview/7/review").queryParam("roundNo", "2")
                        .header("Authorization", "Bearer " + tokenService.issue(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.missingPoint").value("检查和扣减要原子执行"))
                .andExpect(jsonPath("$.data.practiceQuestionId").doesNotExist())
                .andExpect(jsonPath("$.data.practiceQuestion").doesNotExist());
        verify(practiceQuestions).findFirstBySkillTag("REDIS_STOCK_ATOMICITY");
    }

    @Test
    void reviewDoesNotLookUpAnUnrecognizedSkillTag() throws Exception {
        mockReviewSession("ENDED", true);
        mockReviewResponse("""
                {"quote":"加锁","feedback":"提出了加锁思路",
                 "missingPoint":"检查和扣减要原子执行","skillTag":"REDIS_STOCK"}
                """);

        mvc.perform(post("/api/interview/7/review").queryParam("roundNo", "2")
                        .header("Authorization", "Bearer " + tokenService.issue(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.missingPoint").value("检查和扣减要原子执行"))
                .andExpect(jsonPath("$.data.practiceQuestionId").doesNotExist());
        verifyNoInteractions(practiceQuestions);
    }

    @Test
    void practiceQuestionRequiresLoginAndKeepsScoringPointsOffTheReadResponse() throws Exception {
        when(practiceQuestions.findById(1L)).thenReturn(new PracticeQuestionMapper.Details(
                1L, "怎么避免库存超卖？", "原子检查并扣减", "https://redis.io/"));

        mvc.perform(get("/api/interview/practice/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/interview/practice/1").header("Authorization", "Bearer " + tokenService.issue(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.questionText").value("怎么避免库存超卖？"))
                .andExpect(jsonPath("$.data.scoringPoints").doesNotExist())
                .andExpect(jsonPath("$.data.sourceUrl").doesNotExist());
        verifyNoInteractions(chatClient);
    }

    @Test
    void twoAccountsShareTheSameQuestionCacheAndOnlyQueryMysqlOnce() throws Exception {
        when(practiceQuestions.findById(1L)).thenReturn(new PracticeQuestionMapper.Details(
                1L, "怎么避免库存超卖？", "原子检查并扣减", "https://redis.io/"));
        String key = "practice:question:1";
        var cachedValue = new AtomicReference<String>();
        var ttl = ArgumentCaptor.forClass(Duration.class);
        when(cache.get(key)).thenAnswer(call -> cachedValue.get());
        doAnswer(call -> {
            cachedValue.set(call.getArgument(1));
            return null;
        }).when(cache).set(eq(key), anyString(), any(Duration.class));

        for (long userId : new long[]{1L, 2L}) {
            mvc.perform(get("/api/interview/practice/1").header("Authorization", "Bearer " + tokenService.issue(userId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.questionText").value("怎么避免库存超卖？"));
        }

        verify(practiceQuestions).findById(1L);
        verify(cache).set(eq(key), anyString(), ttl.capture());
        assertThat(ttl.getValue()).isBetween(Duration.ofSeconds(60), Duration.ofSeconds(90));
        var json = new ObjectMapper().readTree(cachedValue.get());
        assertThat(json.size()).isEqualTo(2);
        assertThat(json.get("id").asLong()).isEqualTo(1L);
        assertThat(json.get("questionText").asText()).isEqualTo("怎么避免库存超卖？");
    }

    @Test
    void practiceQuestionRechecksTheCacheAfterAcquiringTheLock() throws Exception {
        String key = "practice:question:1";
        String lockKey = "practice:question:lock:1";
        when(cache.get(key)).thenReturn(null, "{\"id\":1,\"questionText\":\"库存扣减题\"}");

        mvc.perform(get("/api/interview/practice/1").header("Authorization", "Bearer " + tokenService.issue(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionText").value("库存扣减题"));

        var token = ArgumentCaptor.forClass(String.class);
        var order = inOrder(cache, redis);
        order.verify(cache).get(key);
        order.verify(cache).setIfAbsent(eq(lockKey), token.capture(), eq(Duration.ofSeconds(5)));
        order.verify(cache).get(key);
        order.verify(redis).execute(any(RedisScript.class), eq(List.of(lockKey)), eq(token.getValue()));
        verifyNoInteractions(practiceQuestions);
    }

    @Test
    void practiceQuestionWaitsForTheCacheWhenAnotherRequestHasTheLock() throws Exception {
        String lockKey = "practice:question:lock:1";
        when(cache.setIfAbsent(eq(lockKey), anyString(), eq(Duration.ofSeconds(5)))).thenReturn(false);
        when(cache.get("practice:question:1")).thenReturn(null, "{\"id\":1,\"questionText\":\"库存扣减题\"}");

        mvc.perform(get("/api/interview/practice/1").header("Authorization", "Bearer " + tokenService.issue(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionText").value("库存扣减题"));

        verifyNoInteractions(practiceQuestions);
        verify(redis, never()).execute(any(RedisScript.class), eq(List.of(lockKey)), anyString());
    }

    @Test
    void practiceQuestionDoesNotQueryMysqlWhenTheLockWaitExpires() throws Exception {
        when(cache.setIfAbsent(eq("practice:question:lock:1"), anyString(), eq(Duration.ofSeconds(5))))
                .thenReturn(false);

        mvc.perform(get("/api/interview/practice/1").header("Authorization", "Bearer " + tokenService.issue(1L)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(2005));

        verifyNoInteractions(practiceQuestions);
    }

    @Test
    void missingPracticeQuestionOnlyQueriesMysqlAgainAfterItsMarkerExpires() throws Exception {
        long questionId = 99L;
        String key = "practice:question:99";
        String authorization = "Bearer " + tokenService.issue(1L);
        var cachedValue = new AtomicReference<String>();
        when(cache.get(key)).thenAnswer(call -> cachedValue.get());
        doAnswer(call -> {
            cachedValue.set(call.getArgument(1));
            return null;
        }).when(cache).set(eq(key), eq("NOT_FOUND"), eq(Duration.ofSeconds(10)));

        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(get("/api/interview/practice/99").header("Authorization", authorization))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(1002));
        }
        verify(practiceQuestions).findById(questionId);
        verify(cache).set(eq(key), eq("NOT_FOUND"), eq(Duration.ofSeconds(10)));

        cachedValue.set(null);
        mvc.perform(get("/api/interview/practice/99").header("Authorization", authorization))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1002));
        verify(practiceQuestions, times(2)).findById(questionId);
    }

    @Test
    void practiceQuestionFallsBackToMysqlWhenRedisCannotBeRead() throws Exception {
        when(cache.get("practice:question:1")).thenThrow(new RedisConnectionFailureException("test Redis outage"));
        when(practiceQuestions.findById(1L)).thenReturn(new PracticeQuestionMapper.Details(
                1L, "怎么避免库存超卖？", "原子检查并扣减", "https://redis.io/"));

        mvc.perform(get("/api/interview/practice/1").header("Authorization", "Bearer " + tokenService.issue(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionText").value("怎么避免库存超卖？"));
        verify(practiceQuestions).findById(1L);
        verify(cache, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void redisFailureRejectsExcessFallbacksAndReusesReleasedPermits() throws Exception {
        when(cache.get("practice:question:1")).thenThrow(new RedisConnectionFailureException("test Redis outage"));
        var queriesStarted = new CountDownLatch(2);
        var allowQueriesToFinish = new CountDownLatch(1);
        when(practiceQuestions.findById(1L)).thenAnswer(call -> {
            queriesStarted.countDown();
            assertThat(allowQueriesToFinish.await(5, TimeUnit.SECONDS)).isTrue();
            return new PracticeQuestionMapper.Details(1L, "库存扣减题", "原子扣减", "https://redis.io/");
        });
        String authorization = "Bearer " + tokenService.issue(1L);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> mvc.perform(get("/api/interview/practice/1")
                    .header("Authorization", authorization)).andExpect(status().isOk()));
            var second = workers.submit(() -> mvc.perform(get("/api/interview/practice/1")
                    .header("Authorization", authorization)).andExpect(status().isOk()));
            assertThat(queriesStarted.await(5, TimeUnit.SECONDS)).isTrue();

            mvc.perform(get("/api/interview/practice/1").header("Authorization", authorization))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value(2005));
            verify(practiceQuestions, times(2)).findById(1L);

            allowQueriesToFinish.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            mvc.perform(get("/api/interview/practice/1").header("Authorization", authorization))
                    .andExpect(status().isOk());
            verify(practiceQuestions, times(3)).findById(1L);
        } finally {
            allowQueriesToFinish.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void practiceAnswerUsesTheSavedRubricAndReturnsItAfterSubmission() throws Exception {
        when(practiceQuestions.findById(1L)).thenReturn(new PracticeQuestionMapper.Details(
                1L, "怎么避免库存超卖？", "原子检查并扣减", "https://redis.io/"));
        ChatClient.ChatClientRequestSpec prompt = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(prompt);
        when(prompt.system(anyString())).thenReturn(prompt);
        when(prompt.user("题目\n怎么避免库存超卖？\n参考评分点\n原子检查并扣减\n候选人作答\n检查够不够")).thenReturn(prompt);
        when(prompt.call()).thenReturn(call);
        when(call.content()).thenReturn("答到了检查库存，还需要说明如何原子扣减。");

        mvc.perform(post("/api/interview/practice/1/answer").header("Authorization", "Bearer " + tokenService.issue(1L))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"answerText":"检查够不够"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feedback").value("答到了检查库存，还需要说明如何原子扣减。"))
                .andExpect(jsonPath("$.data.scoringPoints").value("原子检查并扣减"))
                .andExpect(jsonPath("$.data.sourceUrl").value("https://redis.io/"));
    }

    @Test
    void practiceRejectsBlankAnswersAndMissingQuestionsBeforeCallingTheModel() throws Exception {
        String authorization = "Bearer " + tokenService.issue(1L);
        mvc.perform(post("/api/interview/practice/1/answer").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"answerText":"  "}
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(practiceQuestions);
        mvc.perform(get("/api/interview/practice/99").header("Authorization", authorization))
                .andExpect(status().isNotFound());
        verifyNoInteractions(chatClient);
    }

    @Test
    void reviewRejectsAQuoteMissingFromTheSavedAnswer() throws Exception {
        mockReviewSession("ENDED", true);
        mockReviewResponse("""
                {"quote":"用 Lua 加锁","feedback":"给出了 Lua 做法",
                 "missingPoint":"检查和扣减要原子执行","skillTag":"REDIS_STOCK_ATOMICITY"}
                """);

        mvc.perform(post("/api/interview/7/review").queryParam("roundNo", "2")
                        .header("Authorization", "Bearer " + tokenService.issue(1L)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(2004));
        verifyNoInteractions(practiceQuestions);
    }

    @Test
    void reviewOfAnUnansweredRoundSkipsTheModel() throws Exception {
        mockReviewSession("ENDED", false);

        mvc.perform(post("/api/interview/7/review").queryParam("roundNo", "2")
                        .header("Authorization", "Bearer " + tokenService.issue(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INSUFFICIENT_EVIDENCE"))
                .andExpect(jsonPath("$.data.answerMessageId").doesNotExist());
        verifyNoInteractions(chatClient);
        verifyNoInteractions(practiceQuestions);
    }

    @Test
    void reviewRejectsOngoingAndOtherUsersSessionsBeforeCallingTheModel() throws Exception {
        mockReviewSession("ONGOING", true);
        when(sessions.detail(7L, 2L)).thenThrow(new BizException(1002, HttpStatus.NOT_FOUND, "面试会话不存在"));

        mvc.perform(post("/api/interview/7/review").queryParam("roundNo", "2")
                        .header("Authorization", "Bearer " + tokenService.issue(1L)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/interview/7/review").queryParam("roundNo", "2")
                        .header("Authorization", "Bearer " + tokenService.issue(2L)))
                .andExpect(status().isNotFound());
        verifyNoInteractions(chatClient);
    }

    private void mockReviewSession(String status, boolean answered) {
        InterviewSessionSummary session = new InterviewSessionSummary(7L, "后端", null, status, 2);
        InterviewDetail.Message earlierAnswer = new InterviewDetail.Message(10L, "CANDIDATE", 1, "使用索引", null);
        InterviewDetail.Message question = new InterviewDetail.Message(11L, "INTERVIEWER", 2, "库存扣减怎样防超卖？", null);
        InterviewDetail.Message answer = new InterviewDetail.Message(12L, "CANDIDATE", 2, "加锁\n", null);
        when(sessions.detail(7L, 1L)).thenReturn(new InterviewDetail(session,
                answered ? List.of(earlierAnswer, question, answer) : List.of(earlierAnswer, question)));
    }

    private void mockReviewResponse(String json) {
        ChatClient.ChatClientRequestSpec prompt = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(prompt);
        when(prompt.system(anyString())).thenReturn(prompt);
        when(prompt.user("请以 JSON 复盘以下问答。\n原题\n库存扣减怎样防超卖？\n原答\n加锁\n")).thenReturn(prompt);
        when(prompt.call()).thenReturn(call);
        when(call.entity(any(Class.class))).thenAnswer(invocation -> {
            Class<?> type = invocation.getArgument(0);
            return new BeanOutputConverter<>(type).convert(json);
        });
    }

    @Test
    void detailReturnsNotFoundForAnotherAccount() throws Exception {
        when(sessions.detail(7L, 2L))
                .thenThrow(new BizException(1002, HttpStatus.NOT_FOUND, "面试会话不存在"));

        mvc.perform(get("/api/interview/7").header("Authorization", "Bearer " + tokenService.issue(2L))
                        .queryParam("userId", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1002))
                .andExpect(jsonPath("$.data.messages").doesNotExist());
        verify(sessions).detail(7L, 2L);
        verifyNoInteractions(chatClient, redis);
    }

    @Test
    void ongoingDetailUsesVerifiedUserAndAllowsNoCurrentSession() throws Exception {
        mvc.perform(get("/api/interview/ongoing")).andExpect(status().isUnauthorized());
        InterviewSessionSummary session = new InterviewSessionSummary(7L, "后端", null, "ONGOING", 1);
        InterviewDetail.Message question = new InterviewDetail.Message(11L, "INTERVIEWER", 1, "你如何分页？", null);
        when(sessions.ongoingDetail(1L)).thenReturn(new InterviewDetail(session, List.of(question)));

        mvc.perform(get("/api/interview/ongoing").header("Authorization", "Bearer " + tokenService.issue(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.id").value(7))
                .andExpect(jsonPath("$.data.messages[0].content").value("你如何分页？"));
        mvc.perform(get("/api/interview/ongoing").header("Authorization", "Bearer " + tokenService.issue(2L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(sessions).ongoingDetail(1L);
        verify(sessions).ongoingDetail(2L);
        verifyNoInteractions(chatClient, redis);
    }

    @Test
    void manualEndRequiresAuthenticationAndUsesVerifiedUserId() throws Exception {
        mvc.perform(post("/api/interview/7/end")).andExpect(status().isUnauthorized());
        doThrow(new BizException(1002, HttpStatus.NOT_FOUND, "面试会话不存在"))
                .when(sessions).endSession(7L, 2L);

        mvc.perform(authenticatedPost("/api/interview/7/end", 2L).queryParam("userId", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1002));
        mvc.perform(authenticatedPost("/api/interview/7/end", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(sessions).endSession(7L, 2L);
        verify(sessions).endSession(7L, 1L);
        verifyNoInteractions(chatClient, redis);
    }

    @Test
    void endedSessionRejectsBothAnswerEndpointsBeforeModelCall() throws Exception {
        InterviewSession ended = new InterviewSession();
        ended.setStatus(InterviewSession.STATUS_ENDED);
        when(sessions.getOwnedSessionOrThrow(7L, 1L)).thenReturn(ended);
        String answer = """
                {"roundNo":1,"answerText":"按主键排序"}
                """;

        mvc.perform(authenticatedPost("/api/interview/7/answer", 1L).content(answer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1003));
        assertThat(stream("/api/interview/7/answer", answer))
                .contains("event:problem", "\"code\":1003")
                .doesNotContain("event:delta", "event:done");
        verifyNoInteractions(chatClient);
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
        verify(redis).execute(any(RedisScript.class), eq(List.of("rate:/api/interview/start:user:1")), eq("60"));
    }

    @Test
    void failedStreamDoesNotSavePartialQuestion() throws Exception {
        modelChunks(Flux.just("半道题").concatWith(Flux.error(new IllegalStateException("test model failure"))));

        String body = stream("/api/interview/start", """
                {"resumeText":"项目经历","targetPosition":"后端"}
                """);

        assertThat(body).containsSubsequence("event:delta", "半道题", "event:problem").doesNotContain("event:done");
        verify(sessions, never()).createSessionAndFirstMessage(any(), anyString(), anyString(), anyString());
    }

    @Test
    void ongoingSessionBlocksBothStartEndpointsBeforeModelCall() throws Exception {
        doThrow(new BizException(1003, HttpStatus.CONFLICT, "已有进行中的面试"))
                .when(sessions).ensureNoOngoingSession(1L);
        String start = """
                {"resumeText":"项目经历","targetPosition":"后端"}
                """;

        mvc.perform(authenticatedPost("/api/interview/start", 1L).content(start))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1003));

        assertThat(stream("/api/interview/start", start))
                .contains("event:problem", "\"code\":1003")
                .doesNotContain("event:delta", "event:done");
        verifyNoInteractions(chatClient);
        verify(sessions, never()).createSessionAndFirstMessage(any(), anyString(), anyString(), anyString());
    }

    @Test
    void concurrentStartConflictAfterModelOutputDoesNotConfirmTheStream() throws Exception {
        modelChunks(Flux.just("你如何分页？"));
        when(sessions.createSessionAndFirstMessage(1L, "项目经历", "后端", "你如何分页？"))
                .thenThrow(new BizException(1003, HttpStatus.CONFLICT, "已有进行中的面试"));

        assertThat(stream("/api/interview/start", """
                {"resumeText":"项目经历","targetPosition":"后端"}
                """)).containsSubsequence("event:delta", "event:problem", "\"code\":1003")
                .doesNotContain("event:done");
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
        when(redis.execute(any(RedisScript.class), eq(List.of("rate:/api/interview/start:user:1")), eq("60")))
                .thenReturn(4L);

        mvc.perform(streamRequest("/api/interview/start", "{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        verifyNoInteractions(chatClient, sessions);
    }

    @Test
    void authenticatedAccountsAtTheSameAddressHaveSeparateRateLimits() throws Exception {
        when(askService.ask("题库问题")).thenReturn("题库解答");
        when(redis.execute(any(RedisScript.class), eq(List.of("rate:/ask/question:user:1")), eq("60")))
                .thenReturn(4L);
        when(redis.execute(any(RedisScript.class), eq(List.of("rate:/ask/question:user:2")), eq("60")))
                .thenReturn(1L);
        String question = """
                {"question":"题库问题"}
                """;

        mvc.perform(authenticatedPost("/ask/question", 1L).content(question))
                .andExpect(status().isTooManyRequests());
        mvc.perform(authenticatedPost("/ask/question", 2L).content(question))
                .andExpect(status().isOk());
        mvc.perform(post("/ask/question").contentType(MediaType.APPLICATION_JSON).content(question))
                .andExpect(status().isOk());
        mvc.perform(post("/ask/question").header("Authorization", "Bearer invalid")
                        .contentType(MediaType.APPLICATION_JSON).content(question))
                .andExpect(status().isUnauthorized());

        verify(redis, times(3)).execute(any(RedisScript.class), anyList(), eq("60"));
        for (String source : List.of("user:1", "user:2", "127.0.0.1")) {
            verify(redis).execute(any(RedisScript.class), eq(List.of("rate:/ask/question:" + source)), eq("60"));
        }
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
    @Import({SecurityConfig.class, InterviewController.class, InterviewService.class, InterviewReviewService.class,
            AskController.class,
            PracticeQuestionController.class, PracticeQuestionService.class, GlobalExceptionHandler.class})
    static class WebConfig implements WebMvcConfigurer {

        @Bean
        ChatClient chatClient() {
            return mock(ChatClient.class);
        }

        @Bean
        AskService askService() {
            return mock(AskService.class);
        }

        @Bean
        InterviewSessionService sessions() {
            return mock(InterviewSessionService.class);
        }

        @Bean
        PracticeQuestionMapper practiceQuestions() {
            return mock(PracticeQuestionMapper.class);
        }

        @Bean
        StringRedisTemplate redis() {
            return mock(StringRedisTemplate.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
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
