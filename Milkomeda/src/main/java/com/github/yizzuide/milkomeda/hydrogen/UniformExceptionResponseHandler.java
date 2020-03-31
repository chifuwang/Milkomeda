package com.github.yizzuide.milkomeda.hydrogen;

import com.github.yizzuide.milkomeda.util.DataTypeConvertUtil;
import com.github.yizzuide.milkomeda.util.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * UniformResponseExceptionHandler
 *
 * @author yizzuide
 * @since 2.8.0
 * Create at 2020/03/25 22:47
 */
@Slf4j
@ControllerAdvice // 可以用于定义@ExceptionHandler、@InitBinder、@ModelAttribute, 并应用到所有@RequestMapping中
public class UniformExceptionResponseHandler extends ResponseEntityExceptionHandler {

    @Autowired
    private HydrogenProperties props;
    /**
     * 自定义异常配置列表缓存
     */
    private List<Map<String, Object>> customConfList;

    /** 4xx异常处理 */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatus status, WebRequest request) {
        ResponseEntity<Object> responseEntity = handleExceptionResponse(ex, status.value());
        if (responseEntity == null) {
            return super.handleExceptionInternal(ex, body, headers, status, request);
        }
        return responseEntity;
    }

    /*
    // 方法上单个普通类型（如：String、Long等）参数校验异常（校验注解直接写在参数前面的方式）
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseBody
    public HttpBody constraintViolationException(ConstraintViolationException e) {
        return HttpBody.error(SdErrorCode.REQUEST_PARAM_FORMAT_EXCEPTION, e.getConstraintViolations().iterator().next().getMessage());
    }

    // 对方法上@RequestBody的Bean参数校验的处理
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatus status, WebRequest request) {
        return ResponseEntity.ok(HttpBody.error(SdErrorCode.REQUEST_PARAM_FORMAT_EXCEPTION, ex.getBindingResult().getAllErrors().get(0).getDefaultMessage()));
    }

    // 对方法的Form提交参数绑定校验的处理
    @Override
    protected ResponseEntity<Object> handleBindException(BindException ex, HttpHeaders headers, HttpStatus status, WebRequest request) {
        return ResponseEntity.ok(HttpBody.error(SdErrorCode.REQUEST_PARAM_FORMAT_EXCEPTION, ex.getBindingResult().getAllErrors().get(0).getDefaultMessage()));
    }
     */

    /** 其它服务器内部异常处理 */
    @SuppressWarnings("all")
    @ExceptionHandler(Exception.class)
    public void handleException(Exception e, HttpServletResponse response) throws IOException {
        Map<String, Object> body = props.getUniformExceptionResponse().getBody();
        Object status = body.get("status");
        response.setStatus(status == null ?  500 : Integer.parseInt(status.toString()));
        response.setContentType("application/json; charset=UTF-8");
        PrintWriter writer = response.getWriter();
        Map<String, Object> result = new HashMap<>();

        // 自定义异常处理
        Object customs = body.get("customs");
        if (customs != null) {
            if (this.customConfList == null) {
                this.customConfList = new ArrayList<>();
                Map<String, Map<String, Object>> customsMap = (Map<String, Map<String, Object>>) customs;
                for (String k : customsMap.keySet()) {
                    Map<String, Object> eleMap = customsMap.get(k);
                    Object clazz = eleMap.get("clazz");
                    if (clazz == null) continue;
                    Class<?> expClazz = null;
                    try {
                        expClazz = Class.forName(clazz.toString());
                    } catch (Exception ex) {
                        log.error("Hydrogen load class error with msg: {}", ex.getMessage(), e);
                    }
                    eleMap.put("clazz", expClazz);
                    this.customConfList.add(eleMap);
                }
            }

            for (Map<String, Object> map : this.customConfList) {
                Class<Exception> exceptionClass = (Class<Exception>) map.get("clazz");
                if (exceptionClass.isInstance(e)) {
                    Map<String, Object> exMap = DataTypeConvertUtil.beanToMap(e);
                    putMapElement(map, result, "code", null, exMap);
                    putMapElement(map, result, "message", null, exMap);
                    // 其它自定义key也返回
                    map.keySet().stream().filter(k -> !Arrays.asList("clazz", "status", "code", "message").contains(k) && !result.containsKey(k))
                            .forEach(k ->  putMapElement(map, result, k, null, exMap));
                    writer.println(JSONUtil.serialize(result));
                    writer.flush();
                    return;
                }
            }
        }

        log.error("Hydrogen uniform response exception with msg:{} ", e.getMessage(), e);
        putMapElement(body, result, "code", -1, null);
        putMapElement(body, result, "message", "服务器繁忙，请稍后再试！", null);
        putMapElement(body, result, "error-stack-msg", null, e.getMessage());
        StackTraceElement[] stackTrace = e.getStackTrace();
        if (stackTrace.length > 0) {
            String errorStack = String.format("exception happened: %s \n invoke root: %s", stackTrace[0], stackTrace[stackTrace.length - 1]);
            putMapElement(body, result, "error-stack", null, errorStack);
        }
        writer.println(JSONUtil.serialize(result));
        writer.flush();
    }

    @SuppressWarnings("all")
    public ResponseEntity<Object> handleExceptionResponse(Exception ex, Object presetStatusCode) {
        Map<String, Object> body = props.getUniformExceptionResponse().getBody();
        Map<String, Object> result = new HashMap<>();
        Object exp4xx = body.get(presetStatusCode.toString());
        if (!(exp4xx instanceof Map)) {
            return null;
        }
        Map<String, Object> exp4xxBody = (Map<String, Object>) exp4xx;
        Object statusCode4xx = exp4xxBody.get("status");
        if (statusCode4xx == null || presetStatusCode.equals(statusCode4xx)) {
            presetStatusCode = statusCode4xx;
            return ResponseEntity.status(Integer.parseInt(presetStatusCode.toString())).body(null);
        }

        putMapElement(exp4xxBody, result, "code", presetStatusCode, null);
        putMapElement(exp4xxBody, result, "message", null, null);
        return ResponseEntity.status(Integer.parseInt(statusCode4xx.toString())).body(result);
    }

    /**
     * 添加响应字段
     * @param body          响应字段集
     * @param result        返回map
     * @param originKey     定制的key
     * @param defaultValue  默认的值
     * @param replace       配置中没有设置的字段，使用替换的值
     */
    @SuppressWarnings("all")
    private void putMapElement(Map<String, Object> body, Map<String, Object> result, String originKey, Object defaultValue, Object replace) {
        String key = originKey;
        Object value = body.get(originKey);
        if (value == null && defaultValue != null) { // 未指定的配置字段，如果有默认值
            value = defaultValue;
        } else if (value instanceof Map) { // 别名替换
            Map<String, Object> valueMap = (Map<String, Object>) value;
            key = String.valueOf(valueMap.keySet().toArray()[0]);
            value = valueMap.get(key);
        }
        // 配置中未指定返回的字段，直接返回
        if (value == null) {
            return;
        }
        // 替换是数据来源
        if (replace instanceof Map) {
            Map<String, Object> replaceMap = (Map<String, Object>) replace;
            result.put(key, replaceMap.get(key));
            return;
        }
        // 替换是指定的值
        result.put(key, StringUtils.isEmpty(value) && replace != null ? replace : value);
    }
}