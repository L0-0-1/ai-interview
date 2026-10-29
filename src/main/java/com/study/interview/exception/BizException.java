package com.study.interview.exception;

import org.springframework.http.HttpStatus;

// 自定义业务异常类，继承 RuntimeException 类：
//   1. Spring 事务里只有运行时异常会触发回滚；
//   2. 不用每一层都 throw，自动冒泡直到全局异常处理器接住。
//
// 业务码约定（分段管理，码表集中在这里，别处只引用）：
//   10xx = 请求方的问题（HTTP 4xx）：1001 参数问题；1002 资源不存在
//   20xx = 服务端的问题（HTTP 5xx）：2001 保存失败（2002/2003 预留给更新/删除失败场景）
public class BizException extends RuntimeException {
    //添加异常码，方便统一处理
    private final int code;
    // 本异常对应的 HTTP 状态码：在抛出点声明"我属于哪类问题"，处理器不再一刀切 400
    private final HttpStatus status;
    public BizException(int code, String message) {
        // 默认 400：新码默认按"请求方的问题"处理；404/500 用三参构造器显式声明
        this(code, HttpStatus.BAD_REQUEST, message);
    }
    public BizException(int code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }
    public int getCode() {
        return code;
    }
    public HttpStatus getStatus() {
        return status;
    }
}
