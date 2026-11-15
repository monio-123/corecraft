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
     * 某条提示词配置**展开 `【引用】` 之后**的最终文本（空 / 不存在 ⇒ 空串）。
     *
     * <p>⚠️ **为什么不能直接读 `sys_config`**：配置里可能写着 `【引用】ai.skill.writing`，
     * 那是**给会展开的那条通路看的**。前端 `GET /sys/config/mine` 拿到的是原文
     * ⇒ 原样塞进 prompt 的话，模型收到的是一行字面的「【引用】ai.skill.writing」，
     * **被引的规范一个字都到不了**（2026-11-12 实测踩到）。
     *
     * <p>所以「要拿去当提示词的文本」一律走这个口，和 `questionKey` 那条路同一份展开逻辑。
     */
    String composedPrompt(String key);

    /**
     * 几条知识的正文摘要（**按类型分组**：【学习】/【笔记】…），现查现拼。
     *
     * <p>给**任务执行端**用（2026-11-15 修随堂出题「出不了题」）：出题/考试这类
     * 步骤的材料是前端拼的**出题指令**，正文得后端现捞——前端那条路是
     * 「给 material 就不捞知识」（判卷时 material 是整卷，别再捞一遍几十 KB 正文），
     * 于是任务路径**正文压根没进 prompt**，模型只能答「出不了题」。
     *
     * <p>⚠️ **要复用这一份，不要在别处再实现一遍分组格式**——出题规范里写死了
     * 「材料按类型分组，每组前面标着这一组的类型名」，格式分叉了模型就认不出材料。
     *
     * @param topicIds 要考的几条知识
     * @param userId 归属校验（只取自己的）
     * @return 分组后的正文摘要（无正文时返回空串）
     */
    String digestFor(List<Long> topicIds, Long userId);

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
    record AiDelta(String prompt, String reasoning, String content, String finishReason,
                   Integer tokenPrompt, Integer tokenCompletion) {

        public static AiDelta ofPrompt(String prompt) {
            return new AiDelta(prompt, "", "", "", null, null);
        }

        /**
         * 一帧增量。
         *
         * <p>⚠️ **没有"不带 finishReason"的重载**：那种重载会把 finishReason 静默置空，
         * 而"撞了输出上限"这个信息**丢了界面上还看不出来**（内容是截断的，
         * 用户以为拿到的是完整的一段）。要发增量就把 finishReason 一起带上。
         */
        public static AiDelta of(String reasoning, String content, String finishReason) {
            return new AiDelta("", reasoning, content, finishReason, null, null);
        }
    }

    @FunctionalInterface
    interface AiDeltaSink {
        void accept(AiDelta delta);
    }
}