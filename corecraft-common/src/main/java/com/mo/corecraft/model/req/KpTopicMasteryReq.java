package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 「这条内容参与不参与掌握度」的**单条覆盖**（2026-11-12）。
 *
 * <p>三值语义：
 * <ul>
 *   <li>{@code enabled = true} → 这条参与掌握度（哪怕它属于"笔记"类型）；</li>
 *   <li>{@code enabled = false} → 这条不参与（哪怕它属于"学习"类型 ——
 *       用户说的「总览这类不重要的也可以单独关掉」）；</li>
 *   <li>{@code enabled = null} → **恢复继承类型默认**（清掉这条的显式覆盖）。</li>
 * </ul>
 *
 * <p>⚠️ **null 在这里有真实含义**，所以不能用"null = 不修改"那套通用更新语义
 * （那是 {@code SysDictItemUpdateReq} 的规则）。这个端点的职责就是"把这一格设成
 * 某个明确的三态"，null 就是第三态。
 *
 * <p>⚠️ **为什么单独一个端点**，不塞进通用的 PUT /kp/topic：
 * 通用更新是"保存正文"那条路，前端每次存正文都不会带这个字段 ⇒ 它的 null 是
 * "没这个字段"；而这里的 null 是"恢复继承"。两者塞在一个端点里必然要靠
 * "字段存不存在"来区分，而 JSON 里"没传"和"传 null"在很多客户端上根本分不开。
 */
@Getter
@Setter
public class KpTopicMasteryReq {

    @NotNull(message = "id不能为空!")
    private Long id;

    /** true=参与 / false=不参与 / null=恢复继承类型默认 */
    private Boolean enabled;
}
