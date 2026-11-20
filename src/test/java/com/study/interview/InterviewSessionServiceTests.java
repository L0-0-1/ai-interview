package com.study.interview;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.study.interview.exception.BizException;
import com.study.interview.mapper.InterviewMessageMapper;
import com.study.interview.mapper.InterviewSessionMapper;
import com.study.interview.mapper.UserAccountMapper;
import com.study.interview.model.InterviewSession;
import com.study.interview.service.InterviewSessionService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class InterviewSessionServiceTests {

    @Test
    void filtersByAccountAndRequestedStatusInThePaginationQuery() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "history-test"), InterviewSession.class);
        InterviewSessionMapper sessions = mock(InterviewSessionMapper.class);
        InterviewSessionService service = new InterviewSessionService(sessions,
                mock(InterviewMessageMapper.class), mock(UserAccountMapper.class));
        when(sessions.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<InterviewSession> query = invocation.getArgument(1);
            assertThat(query.getSqlSegment()).contains("user_id =", "status =", "ORDER BY id DESC");
            assertThat(query.getParamNameValuePairs()).containsValues(7L, "ENDED");
            return invocation.getArgument(0);
        });

        service.history(7L, 1, 10, "ENDED");
    }

    @Test
    void checksForConcurrentStartAfterTakingTheAccountLock() {
        UserAccountMapper accounts = mock(UserAccountMapper.class);
        InterviewSessionMapper sessions = mock(InterviewSessionMapper.class);
        InterviewMessageMapper messages = mock(InterviewMessageMapper.class);
        InterviewSessionService service = new InterviewSessionService(sessions, messages, accounts);
        when(accounts.lockById(7L)).thenReturn(7L);
        when(sessions.findOngoingSessionId(7L)).thenReturn(101L);

        assertThatThrownBy(() -> service.createSessionAndFirstMessage(7L, "项目经历", "后端", "第一题"))
                .isInstanceOfSatisfying(BizException.class, conflict -> {
                    assertThat(conflict.getCode()).isEqualTo(1003);
                    assertThat(conflict.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });

        InOrder order = inOrder(accounts, sessions);
        order.verify(accounts).lockById(7L);
        order.verify(sessions).findOngoingSessionId(7L);
        verifyNoMoreInteractions(accounts, sessions);
        verifyNoInteractions(messages);
    }
}
