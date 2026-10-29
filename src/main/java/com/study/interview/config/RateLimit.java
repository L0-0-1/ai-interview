package com.study.interview.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限流注解：挂在「调 AI、真花钱」的接口方法上，声明该接口的调用配额。
 *
 * 为什么做限流：一次 AI 调用按 token 计费，接口裸奔等于把钱包放在公网上——
 * 恶意脚本循环刷 /ask，钱被刷走，正常用户还被拖慢。真实企业都有「配额/限流」这一层，
 * 面试问「怎么防止接口被刷」，答案里必须有它。
 *
 * 用法：@RateLimit(limit = 5, windowSeconds = 60) = 同一个来源 60 秒内最多调 5 次。
 * 两个参数按接口成本配：贵的（简历评审，全文进模型）配小，便宜的（简单问答）配大。
 *
 * 注解的「三个零件」都在本文件顶部：
 *   @Target(ElementType.METHOD)   —— 只许挂在方法上（挂类上编译期直接报错）；
 *   @Retention(RUNTIME)           —— 注解要活到运行期：拦截器是在运行期用反射读它的；
 *   @interface                    —— 声明一个注解类型（和 interface 同族语法）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 窗口内允许的最大调用次数 */
    int limit() default 5;

    /** 窗口长度（秒） */
    int windowSeconds() default 60;
}
