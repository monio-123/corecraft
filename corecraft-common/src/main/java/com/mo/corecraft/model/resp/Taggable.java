package com.mo.corecraft.model.resp;

/**
 * 带标签的知识点。
 *
 * <p><b>为什么要这个接口</b>：结构索引那份（{@link KpTopicIndexResp}）和
 * 详情那份（{@link KpTopicResp}）**都要挂标签**，而"挂标签"这条规则
 * （批量查 → 没有标签就不写这个字段 → 有就写成 JSON 数组字符串）只有一份。
 * 两边的响应类型没有共同父类，规则就容易各写一遍——改了一处另一处就对不上，
 * 而症状只是"索引里的标签和详情里的标签不一样"，**界面上看不出任何异常**。
 *
 * <p>有了它，服务层那句"把这批知识点的标签写回去"就只写一次。
 */
public interface Taggable {

    Long getId();

    /**
     * 标签：**JSON 数组字符串**（如 {@code ["索引","锁"]}）。
     *
     * <p>⚠️ **空标签不写这个字段**（保持 null）——"没有标签"和"标签是空数组"
     * 对前端不是一回事。
     */
    void setTags(String tags);
}
