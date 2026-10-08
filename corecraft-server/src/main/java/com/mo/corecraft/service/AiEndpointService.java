package com.mo.corecraft.service;

import com.mo.corecraft.model.entity.AiEndpoint;
import com.mo.corecraft.model.req.AiEndpointSaveReq;
import com.mo.corecraft.model.resp.AiEndpointResp;

import java.util.List;

/**
 * AI 接口（每人多个）。
 *
 * 方法一律带 userId 参数，由 controller 从登录态取。service 不碰 SecurityUtil——
 * 那是 web 层的事，它只管业务。
 */
public interface AiEndpointService {

    /** 列出某人的全部接口。没配过返回空列表，不是 null */
    List<AiEndpointResp> list(Long userId);

    /** 保存（id 为空则新建，有值则更新并沿用留空的字段） */
    AiEndpointResp save(Long userId, AiEndpointSaveReq req);

    /** 删掉一个接口 */
    void delete(Long userId, Long id);

    /**
     * 取"能直接调模型"的那一条，含密钥。总开关没开、或这一条没配齐，就抛异常。
     *
     * **判据收敛在这里一处**：开关（ai_config）和接口（ai_endpoint）是两张表，
     * "能不能用"要同时看两边；散开判的话各处都会漏掉一半。
     */
    AiEndpoint requireReady(Long userId, Long endpointId);
}