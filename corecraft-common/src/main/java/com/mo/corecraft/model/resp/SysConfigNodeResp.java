package com.mo.corecraft.model.resp;

import lombok.Getter;
import lombok.Setter;

/**
 * 配置树节点（管理端和业务端看到的那一份）。
 *
 * **扁平的，不带 children**：前端要的是"按 id 挂上去"的树，自己拼一次比后端再包一层省事；
 * 而且编辑时按 id 找节点比按路径找更直接。
 *
 * **valueType 为空 = 容器节点**（它下面是一组，不是值）。
 */
@Getter
@Setter
public class SysConfigNodeResp {

    private Long id;

    private Long parentId;

    private String name;

    private String code;

    /** 完整配置键 = 从根到本节点的 code 用 "." 连起来 */
    private String configKey;

    /** number | text；容器节点为空 */
    private String valueType;

    private String remark;
}
