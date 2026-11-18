package com.mo.corecraft.service;

import com.mo.corecraft.model.entity.AiEndpoint;
import com.mo.corecraft.model.req.AiEndpointSaveReq;
import com.mo.corecraft.model.req.AiEndpointTestReq;
import com.mo.corecraft.model.resp.AiEndpointResp;
import com.mo.corecraft.model.resp.AiEndpointTestResp;

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
     * 取"能直接调模型"的那一条，含密钥。没选好、或这一条没配齐，就抛异常。
     *
     * <p>⚠️ **不再看「AI 总开关」**（2026-11-16 D6 开关退役）：那个开关把三类
     * 互不相干的行为绑在一起（交互类 / 一次点击多次调用 / 零点击自主任务），
     * 关掉它三件一起变；"能不能用 AI"的真相只有一条——**端点配全了没有**。
     * 急停 = 停用或删除端点。
     */
    AiEndpoint requireReady(Long userId, Long endpointId);

    /**
     * **当前端点是不是已经配全**（免抛异常）。
     *
     * <p>给自主任务用（命名）：它要的是"能不能跑"的答案，不是"为什么不能跑"的报错——
     * 没配好就不跑（留空标题），用户那边什么提示都不出。
     */
    boolean currentReady(Long userId);

    /**
     * 测一条配置能不能真调通。**不通是结果，不是异常**（见 AiEndpointTestResp）：
     * 上游 401/404/超时一律正常返回 ok=false + 上游原文，交给界面内联显示。
     *
     * <p>测的是「地址 + 密钥 + 协议 + 模型名」四关，走的是真的一次最小对话；
     * 请求形状与错误识别都收敛在 {@code AiProtocol}，这里不认任何厂商/模型。
     */
    AiEndpointTestResp test(Long userId, AiEndpointTestReq req);
}