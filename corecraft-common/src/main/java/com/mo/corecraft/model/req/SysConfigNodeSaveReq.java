package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 新建 / 改一个配置树节点。
 *
 * **只有通用的四样**：名称（中文描述）/ 编码 / 值类型 / 说明。
 *
 * ⚠️ **没有"最小 / 最大 / 步长 / 单位 / 内置默认"**（2026-10-21 用户反馈："还有耦合问题"）：
 * 配置项是**通用的系统级 KV**，新建一条普通 KV 时界面上不该凭空多出
 * 五个填不上的输入框 —— 滑块范围是**界面偏好这一件专属的**东西，
 * 塞进通用结构就是耦合。范围怎么说明？写在「说明」里（人能读懂那句话）。
 *
 * **code 和 parentId 建好之后不可改**（更新时传错会直接报错）：
 * 键 = 根到本节点的 code 连起来的路径，改了键就变了，已经存进去的值会变成孤儿。
 * 要换父节点或换短名，删了重建更干脆。
 */
@Getter
@Setter
public class SysConfigNodeSaveReq {

    /** 改的时候带；新建时为空 */
    private Long id;

    /** 父节点；0 = 顶层 */
    @NotNull(message = "父节点不能为空!")
    private Long parentId;

    /** 显示名（中文描述） */
    @NotBlank(message = "名称不能为空!")
    @Size(max = 64, message = "名称长度不能超过 64 个字符!")
    private String name;

    /** 短名，同一父下唯一。键的末段 */
    @NotBlank(message = "短名不能为空!")
    @Size(max = 64, message = "短名长度不能超过 64 个字符!")
    private String code;

    /** number | text；留空 = 容器节点（它下面是一组，不是一个值） */
    @Size(max = 16, message = "值类型长度不能超过 16 个字符!")
    private String valueType;

    /** 说明（给人看的）。范围、步长这类信息写在这儿 */
    @Size(max = 255, message = "说明长度不能超过 255 个字符!")
    private String remark;
}
