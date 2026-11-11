package com.study.interview.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 配置：把限流拦截器挂上处理链。
 *
 * 拦截路径只圈「调 AI 的三片」（/ask、/api/resume、/api/interview）：
 * 拦截器在每个请求进 Controller 前都要跑一遍，路径圈得越宽，无关请求白付的开销越大。
 * 具体哪个接口要限流、限多少，由方法上的 @RateLimit 注解精确圈定——
 * 拦截器管「到哪去找注解」，注解管「谁要限流、限多少」，各司其职。
 * （/api/resume/history 是 GET 查询、不调 AI，但挂在同一路径下也没关系：
 * 它没注解，拦截器查不到就放行。）
 *
 * 注：/api/interview/** 是面试模块新增后补上的——不加这条，面试接口上的
 * @RateLimit 注解写了也不会生效（拦截器根本不会路过这些路径）。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    public WebConfig(RateLimitInterceptor rateLimitInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/ask/**", "/api/resume/**", "/api/interview/**");
    }
}
