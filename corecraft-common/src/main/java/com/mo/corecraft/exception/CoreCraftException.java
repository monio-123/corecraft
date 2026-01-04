package com.mo.corecraft.exception;

import com.mo.corecraft.enums.ResultCodeEnum;
import lombok.Getter;

@Getter
public class CoreCraftException extends RuntimeException{

    private ResultCodeEnum resultCode;

    public CoreCraftException(String message) {
        super(message);
    }

    public CoreCraftException(ResultCodeEnum resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }
}
