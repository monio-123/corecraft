package com.mo.corecraft.model.resp;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KpTagResp {

    private Long id;

    private String name;

    /** 被多少个 topic 引用（前端管理 UI 展示用） */
    private Long refCount;
}
