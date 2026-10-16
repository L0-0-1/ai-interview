package com.study.interview.exception;

import com.study.interview.common.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBizException(BizException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 接"参数类型不匹配"：请求方把参数填成了翻译不动的格式（如 size=abc、id=update）。
     * 这是发生在【进方法之前】的框架异常——业务代码根本没执行，没机会抛 BizException，
     * 所以必须单独建筛子；否则只能掉进兜底，被误报成"服务器故障"500。
     * 语义归类：请求方填错 → 400；业务码用约定表中的 1001（参数问题）。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        // e.getName() 取参数名（如 size）；另外 e.getValue() 能取到收到的原文本（如 "abc"），此处不展示
        return Result.error(1001, "参数 " + e.getName() + " 格式不正确");
    }

    /**
     * 接「必填参数没传」：URL 上少了必需的参数（如评审接口不带 resumeText）。
     * 与参数类型不匹配同一性质：发生在【进方法之前】，业务代码没执行——
     * 是请求方给少了，不是服务器坏了 → 400（不单独接住就会被兜底吞成 500）。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        // e.getParameterName() 是缺的那个参数名，直接告诉请求方「缺了啥」
        return Result.error(1001, "缺少必填参数：" + e.getParameterName());
    }

    /**
     * 接"请求方法不匹配"：路径是通的，但请求用的 HTTP 方法不被支持——
     * 典型场景：浏览器地址栏直接敲只收 POST 的 /api/resume/save（地址栏只能发 GET）。
     * 与类型不匹配同一性质：异常由框架在【进方法之前】抛出，业务代码没机会自己处理；
     * 不单独建筛子就会掉进兜底，被误报成"服务器故障"500——实际是请求方用错了方法。
     * 语义归类：请求方方法用错 → 405（方法不允许）。
     * （该场景自 /save 从 GET 改成 POST 后才第一次出现：此前写接口全是 GET，不会"方法不匹配"。）
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        // e.getMethod() 是请求方实际用的方法名（如 GET），把"你用错了啥"回话过去
        return Result.error(405, "该路径不支持 " + e.getMethod() + " 方法，请检查请求方式");
    }

    /**
     * 接"请求体读不动"：请求体不是合法 JSON（手写漏引号、尾逗号、括号不配对等）。
     * @RequestBody 的约定是"把请求体翻译成对象"——翻译失败就到这里，
     * 同属【进方法之前】的框架异常。是请求方发错了内容 → 400。
     * （不单列就会掉进兜底变成 500，让人误以为服务器坏了，其实只是 JSON 写错了。）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMessageNotReadable(HttpMessageNotReadableException e) {
        // 不回显 e.getMessage()：里面带着原始 JSON 片段和行列号，属于内部细节；给一行操作指引就够
        return Result.error(400, "请求体不是合法 JSON，请检查格式");
    }

    /**
     * 接"请求体类型不支持"：请求头 Content-Type 没标 JSON（或标了别的类型），
     * 服务端无法确定用什么方式解析请求体。请求方把"内容类型"标错 → 415。
     * 与 405 同一逻辑：路径对、但方法或类型用错了。
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public Result<Void> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return Result.error(415, "请求体格式不支持，请使用 Content-Type: application/json");
    }

    /**
     * 接「地址不存在」：路径上没有任何接口认领，静态资源里也没有同名文件。
     * 新版框架（Spring 6.1 起）对没匹配上的路径会走「静态资源」通道找一圈，
     * 找不到就抛「找不到资源」异常——它同属【请求方给错了地址】这一族，
     * 不单独接住就会被兜底吞成 500（这个坑在本项目的第四个成员，前三：405/400/415）。
     * 语义归类：没有这个地址 → 404。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResourceFound(NoResourceFoundException e) {
        // 不回显异常细节（里面带着被查找的完整路径）；给一句人话指引就够
        return Result.error(404, "地址不存在，请检查路径是否正确");
    }
    // 统一处理未知异常
    @ExceptionHandler
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleUnknownException(Exception e) {
        e.printStackTrace();
        return Result.error(500, "服务器内部错误，请稍后重试");
    }
}
