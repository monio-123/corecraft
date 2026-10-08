package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 移动目录到新父级。
 *
 * **newParentId 传 null 或 0 = 移到顶层**（侧边栏目录区接收拖拽走的就是这条）。
 */
@Getter
@Setter
public class KpCategoryMoveReq {

    @NotNull(message = "目录ID不能为空!")
    private Long id;

    /** 目标父目录；null / 0 表示移到顶层 */
    private Long parentId;
}
