package com.study.interview.exception;
//自定义业务异常类，继承RuntimeException类，1，在Spring事务中只有运行时异常可以回滚，2，不用每一层都throw，自动冒泡直到有异常处理器处理接住
public class BizException extends RuntimeException {
    //添加异常码，方便统一处理
    private final int code;
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
    public int getCode() {
        return code;
    }
}
