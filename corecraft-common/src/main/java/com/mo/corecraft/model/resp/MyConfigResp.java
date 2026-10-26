package com.mo.corecraft.model.resp;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 我的配置：生效值 + 系统默认原值。
 *
 * **两个 map 分开给**：values 是"现在生效的"，defaults 是"平台默认长什么样"。
 * 前端要靠两者的差值判断"这一项被我改过 / 可以恢复默认"，合成一个 map 这个信息就没了。
 *
 * **值一律是字符串**：列是 varchar，前端自己转数字。
 */
@Getter
@Setter
public class MyConfigResp {

    /** 生效值 = 我的覆盖 > 系统默认 */
    private Map<String, String> values = new LinkedHashMap<>();

    /** 系统默认（user_id=0 那几行）的原值 */
    private Map<String, String> defaults = new LinkedHashMap<>();
}
