package com.mo.corecraft.service;

import com.mo.corecraft.model.req.AiChatReq;
import com.mo.corecraft.model.resp.AiQuestionResp;

import java.io.IOException;
import java.util.List;

/**
 * 就某条知识提问。
 *
 * 方法一律带 userId 不碰 SecurityUtil —— 跟 AiConfigService 一个规矩，那东西是 web 层的事。
 * **零写入**：问完就走，一个字都不回写到 kp_topic。
 */
public interface AiChatService {

    /**
     * 抽屉空态那几个引导问题。来自配置树上的「引导问题」那组，改问题不用改代码。
     *
     * **配置树拉不到时返回空列表**，前端空态什么都不显示——不该为一个可有可无的引导
     * 列表让整页报错。
     */
    List<AiQuestionResp> questions();

    /**
     * 流式回答。**阻塞**——一直读到模型答完，中途每产出一段就喂给 deltaSink。
     *
     * 异步是 Web 层的事（SseEmitter 要在 handler 返回之后才写得进去），所以这个方法
     * 保持纯阻塞，由 controller 决定在哪个线程上跑。
     *
     * **整个提问参数打包成 req 传进来**，不是拆成一串形参：它们已经涨到 6 个
     * （userId / endpointId / topicId / question / questionKey / history），
     * 其中三个是相邻的 String——**位置传错编译器不报错，症状是"AI 答非所问"，
     * 排查起来比空指针还费劲**。要加参数就往 req 上加，不会引出这类错。
     *
     * @param req 提问参数。material 决定这次处理哪份素材（空 = 这条知识自己的正文）；
     *            endpointId 的归属和配齐情况由 AiEndpointService 校验；history 为空 = 第一轮
     * @param deltaSink 收到一个事件。四种可能（靠哪个字段非空区分）：
     *                  <ul>
     *                    <li><b>prompt</b>  —— 开流前发一次，完整提交给模型的消息；</li>
     *                    <li><b>reasoning</b> —— 思考过程增量（**只有思考型模型才给**，
     *                        deepseek-flash 这类非思考模型恒为空串）；</li>
     *                    <li><b>content</b> —— 正文增量；</li>
     *                    <li><b>finishReason</b> —— 收尾时才有（上游说"为什么停了"）。</li>
     *                  </ul>
     *                  用一个 sink 而不是三个回调，是因为它们本来就是同一条流上的三种帧，
     *                  拆成三个参数只会让调用方多写两行。
     * @throws com.mo.corecraft.exception.CoreCraftException 配置不可用 / 知识点不存在 / 全局人设取不到
     * @throws IOException 调上游失败（连不上、超时、非 2xx）
     */
    void stream(Long userId, AiChatReq req, AiDeltaSink deltaSink) throws IOException;

    /**
     * 一帧。四个字段里 prompt 是一次性的，reasoning / content 是连续增量的，
     * finishReason 是**收尾时才有**的（上游说"为什么停了"）。
     *
     * @param finishReason 上游的结束原因（OpenAI 兼容协议里是 {@code choices[0].finish_reason}）：
     *                     {@code stop} = 正常写完；{@code length} = **撞了输出上限，内容是截断的**。
     *                     空串 = 上游没给。
     */
    record AiDelta(String prompt, String reasoning, String content, String finishReason) {

        public static AiDelta ofPrompt(String prompt) {
            return new AiDelta(prompt, "", "", "");
        }

        /**
         * 一帧增量。
         *
         * <p>⚠️ **没有"不带 finishReason"的重载**：那种重载会把 finishReason 静默置空，
         * 而"撞了输出上限"这个信息**丢了界面上还看不出来**（内容是截断的，
         * 用户以为拿到的是完整的一段）。要发增量就把 finishReason 一起带上。
         */
        public static AiDelta of(String reasoning, String content, String finishReason) {
            return new AiDelta("", reasoning, content, finishReason);
        }
    }

    @FunctionalInterface
    interface AiDeltaSink {
        void accept(AiDelta delta);
    }
}