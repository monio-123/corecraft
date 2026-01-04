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

    public static <T> ResultResp<T> success(T data) {
        return new ResultResp<>(ResultCodeEnum.SUCCESS, data);
    }

    public static <T> ResultResp<T> fail(int code, String message) {
        return new ResultResp<>(ResultCodeEnum.FAIL, null);
    }

    public static <T> ResultResp<T> data(T data) {
        return new ResultResp<>(ResultCodeEnum.SUCCESS, data);
    }

}
