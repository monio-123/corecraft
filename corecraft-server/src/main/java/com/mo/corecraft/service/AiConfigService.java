package com.mo.corecraft.service;

import com.mo.corecraft.model.req.AiConfigSaveReq;
import com.mo.corecraft.model.resp.AiConfigResp;

/**
 * AI 配置。
 *
 * **为什么单独一个 service 而不塞进 sys 系列**：它按 user_id 隔离、和用户一一绑定，
 * 跟字典那种"全局参考数据"是两种东西。混进 Sys* 系列会让"全局 / 按用户"这条界线糊掉。
 *
 * 方法一律带 userId 参数，由 controller 从登录态取。service 不碰 SecurityUtil——
 * 那是 web 层的事，它只管业务。
 */
public interface AiConfigService {

    /** 读某人的开关。没设过返回 enabled=false，前端据此隐藏所有 AI 入口 */
    AiConfigResp select(Long userId);

    /** 保存开关（不存在则新建） */
    AiConfigResp save(Long userId, AiConfigSaveReq req);

    /** 总开关开没开。接口配没配齐不管——那件事归 AiEndpointService.requireReady */
    boolean isEnabled(Long userId);
}