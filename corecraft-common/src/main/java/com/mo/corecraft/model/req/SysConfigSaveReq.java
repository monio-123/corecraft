package com.mo.corecraft.model.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 批量保存配置项：{ "items": [ { "key": "ui.sideWidth", "value": "280" } ] }
 *
 * **为什么批量而不是一个键一次**：前端拖完滑块一次性提交，
 * 一个键一次请求 = 一次往返 + 一条 SQL，没有意义。
 *
 * **用户端和管理端共用这一个 DTO**：两边 JSON 形状一样（管理端多一个 remark），
 * 区别只是"写到谁的 user_id 下"，拆成两个类只是把同一份形状抄两遍。
 */
@Getter
@Setter
public class SysConfigSaveReq {

    @NotEmpty(message = "配置项不能为空!")
    @Valid
    private List<SysConfigItemReq> items;
}
