package com.study.interview.exception;

/**
 * 限流异常：请求超过配额时由限流拦截器抛出。
 *
 * 为什么不复用 BizException：BizException 在全局处理器里固定翻成 HTTP 400
 * （语义是「请求本身有问题」）；而超配额是「请求合法、频率过头」，
 * 标准语义是 429 Too Many Requests——让客户端一眼知道自己被限流了，而不是以为接口坏了。
 * 继承 RuntimeException 的理由与 BizException 相同：自由冒泡到全局异常出口。
 */
public class RateLimitException extends RuntimeException {

    public RateLimitException(String message) {
        super(message);
    }
}
