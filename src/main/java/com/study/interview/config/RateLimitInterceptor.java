package com.study.interview.config;

import com.study.interview.exception.RateLimitException;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 限流拦截器：请求进入 Controller 之前，在 Redis 里数一数「这个来源最近调了几次」。
 *
 * 为什么用 Redis 计数，不用内存里的 Map：
 *   1. Map 存在 JVM 里：重启清零（刷子掐着重启点继续刷）、多实例部署各数各的（形同虚设）；
 *   2. Redis 是独立进程，所有实例共享同一份计数；INCR 单线程原子，天生适合当计数器。
 *
 * 为什么用 Lua 脚本而不是两条命令：
 *   「INCR 计数 → 首次计数为 1 时 EXPIRE 设过期」是两步操作；
 *   两步之间进程崩溃，key 就永远不过期 → 这个来源被永久限死。
 *   脚本在 Redis 服务端一次性执行，两条命令之间插不进任何别的操作，天然原子。
 *
 * 一个刻意的边界决定：Redis 连不上时【放行】而不是拒绝（故障开放 fail-open）——
 *   限流是「保护钱包」的附加功能，不能变成「Redis 一挂，整个业务全瘫」的单点；
 *   钱包的最终防线是模型服务商的配额上限，这里的限流是前置闸门。
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;

    public RateLimitInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // Lua 脚本：计数 +1；若是窗口内第一次计数，同时设过期时间（两件事原子完成）。
    // KEYS[1] = 限流 key，ARGV[1] = 窗口秒数；返回值 = 加完后的计数值。
    private static final DefaultRedisScript<Long> INCR_WITH_EXPIRE = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1])\n"
                    + "if count == 1 then\n"
                    + "  redis.call('EXPIRE', KEYS[1], ARGV[1])\n"
                    + "end\n"
                    + "return count",
            Long.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // SSE 完成时的异步分派属于同一次请求，不能再次计入限流。
        if (request.getDispatcherType() == DispatcherType.ASYNC) {
            return true;
        }
        // 只有「真 Controller 方法」才查注解：静态资源、默认页的 handler 不是 HandlerMethod，直接放行
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return true; // 没挂注解的接口不限流（历史查询等免费接口不受影响）
        }

        // 「来源」用客户端 IP 识别（本项目还没有登录体系，IP 是现实选择）。
        // 已知局限：同一 NAT 下多人共用 IP 会被误伤；挂代理时取真实 IP 要读 X-Forwarded-For，
        // 而那个头可以被伪造——生产要么先上登录体系按用户限，要么在可信代理层取 IP。
        String key = "rate:" + request.getRequestURI() + ":" + request.getRemoteAddr();

        Long count;
        try {
            count = redisTemplate.execute(INCR_WITH_EXPIRE, List.of(key), String.valueOf(rateLimit.windowSeconds()));
        } catch (Exception e) {
            // 故障开放：Redis 挂了或网络抖动时放行，不能让限流把业务一起拖死
            e.printStackTrace();
            return true;
        }

        if (count != null && count > rateLimit.limit()) {
            // 抛出去交给全局异常处理器统一翻成 429 + Result 信封（和项目所有失败同一个形态）
            throw new RateLimitException("请求太频繁，请稍后再试（"
                    + rateLimit.windowSeconds() + " 秒内最多 " + rateLimit.limit() + " 次）");
        }
        return true;
    }
}
