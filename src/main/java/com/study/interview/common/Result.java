package com.study.interview.common;

public class Result<T> {
    //状态码
    private final int code;
    //描述错误信息
    private final String message;
    //返回数据，如果错误则返回null
    private final T data;
    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }
    //成功时状态码为0（非0即失败，与HTTP状态码分家）
    public static <T> Result<T> success(T data) {
        return new Result<>(0, "success", data);
    }
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }
    //提供get方法为json序列化做准备，方便前端获取数据
    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }
}
