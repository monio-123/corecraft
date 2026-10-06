package com.mo.corecraft.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI 接口：一人多行，提问时选一行用。
 *
 * **为什么没有 provider 字段**：厂商由 {@code baseUrl} 决定。OpenAI 兼容协议下
 * 通义 / DeepSeek / Moonshot / 智谱 / OpenAI 本家是同一个请求格式，换厂商只改
 * baseUrl 和 model，代码零改动。真碰到不兼容的厂商才需要写适配器——到那时再加字段。
 *
 * **name 是用户自己起的别名**，不是厂商名：同一家可以配多个模型（deepseek-chat / deepseek-reasoner），
 * 光看 base_url 分不清两条，列表里也就没法选。
 *
 * **不继承 BaseEntity**：不需要 is_delete（删了再建即可），时间交给数据库默认值。
 */
@Getter
@Setter
@TableName("ai_endpoint")
public class AiEndpoint {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 自己起的名字，例："日常问答" / "写代码" */
    private String name;

    /** OpenAI 兼容接口地址，例：https://api.deepseek.com/v1 */
    private String baseUrl;

    private String model;

    /** 只存后端，Resp 里绝不带出去 */
    private String apiKey;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}