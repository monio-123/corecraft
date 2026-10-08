package com.mo.corecraft.exception;

import com.mo.corecraft.enums.ResultCodeEnum;
import com.mo.corecraft.model.resp.ResultResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CoreCraftException.class)
    public ResultResp<?> handleCoreCraftException(CoreCraftException e) {
        log.error("CoreCraftException: ", e);
        return ResultResp.fail(e.getResultCode().getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class})
    public ResultResp<?> handleValidationException(Exception ex) {
        BindingResult bindingResult = ((MethodArgumentNotValidException) ex).getBindingResult();
        String message = "参数错误";
        List<FieldError> errors = bindingResult.getFieldErrors();
        if (!errors.isEmpty()) {
            message = errors.get(0).getDefaultMessage();
        }
        return ResultResp.fail(message);
    }

    @ExceptionHandler(Exception.class)
    public ResultResp<?> handleException(Exception e) {
        log.error("Exception: ", e);
        return ResultResp.fail(ResultCodeEnum.FAIL.getCode(), e.getMessage());
    }
}
