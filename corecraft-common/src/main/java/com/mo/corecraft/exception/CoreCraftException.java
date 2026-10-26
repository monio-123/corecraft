package com.mo.corecraft.exception;

import com.mo.corecraft.enums.ResultCodeEnum;
import lombok.Getter;

@Getter
public class CoreCraftException extends RuntimeException{

    private ResultCodeEnum resultCode;

    public CoreCraftException(String message) {
        super(message);
        // **必须有 resultCode**：全局异常处理器读的是 `e.getResultCode().getCode()`，
        // 这里不填就是 null ⇒ 处理器自己 NPE ⇒ Spring 回一份默认的 500 错误页，
        // **那句中文提示永远到不了前端**（用户只看到"请求失败（500）"）。
        // 2026-10-26 修：这条业务异常在全站有几十处（AI 问答、AI 接口、引导问题……），
        // 之前它们的中文提示其实一条都没送达过。
        this.resultCode = ResultCodeEnum.FAIL;
    }

    public CoreCraftException(ResultCodeEnum resultCode) {
        super(resultCode.getMessage());
    }

    public CoreCraftException(ResultCodeEnum resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }
}
