package com.mo.corecraft.model.resp;

import lombok.Getter;
import lombok.Setter;

/**
 * 目录列表项。
 * <p>
 * 不返回 refCount：前端 store 已持有全部 topic，目录下的条数在 store 里 group by 一下
 * 就有，不必为此在 SQL 里再 group by 一次。
 */
@Getter
@Setter
public class KpCategoryResp {

    private Long id;

    private String name;

    /** 父目录 id，0 = 顶层。前端据此渲染层级和面包屑 */
    private Long parentId;
}
