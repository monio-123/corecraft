package com.mo.corecraft.model.resp;

import com.mo.corecraft.enums.ResultCodeEnum;
import lombok.Getter;

@Getter
public class ResultResp<T> {

    private final int code;

    private final String message;

    private final T data;

    public ResultResp(ResultCodeEnum resultCode, T data) {
        this.code = resultCode.getCode();
        this.message = resultCode.getMessage();
        this.data = data;
    }

    public ResultResp(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ResultResp<T> success() {
        return new ResultResp<>(ResultCodeEnum.SUCCESS, null);
    }

    public static <T> ResultResp<T> success(T data) {
        return new ResultResp<>(ResultCodeEnum.SUCCESS, data);
    }

    public static <T> ResultResp<T> fail() {
        return new ResultResp<>(ResultCodeEnum.FAIL, null);
    }

    public static <T> ResultResp<T> fail(String message) {
        return new ResultResp<>(ResultCodeEnum.FAIL.getCode(), message, null);
    }

    public static <T> ResultResp<T> fail(int code, String message) {
        return new ResultResp<>(code, message, null);
    }

    public static <T> ResultResp<T> data(T data) {
        return new ResultResp<>(ResultCodeEnum.SUCCESS, data);
    }

}
