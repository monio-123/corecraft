package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 就某条（或某几条）知识提问。
 *
 * **正文默认由服务端自己从库里捞**：前端指定"要读哪条存储的知识"会带来两个问题
 * ——body 可能大到几 MB，以及"客户端能指定把哪段文本当上下文"等于给了绕过数据边界的一条路。
 *
 * **这次处理哪几条知识，三种来源，优先级从高到低**：
 * <ol>
 *   <li>{@link #topicIds} —— 多条（考一个目录 = 这个目录下的所有知识一起出题）；</li>
 *   <li>{@link #material} —— 用户自己敲的一段文字（不指向任何存储数据，所以不涉及越权）；</li>
 *   <li>{@link #topicId} —— 单条（抽屉问一条知识）。</li>
 * </ol>
 *
 * 归属一律按登录态收：**不是你的知识查不到**，查不到报"知识点不存在或已删除"，
 * 不区分"不存在"和"不是你的"（免得泄露别人的数据存在性）。
 *
 * endpointId 由前端传（当前选中哪个接口存在 localStorage 里），**不接受 userId**：
 * 传了就能用别人的密钥提问。
 */
@Getter
@Setter
public class AiChatReq {

    /**
     * **单条**知识的 id。留空时必须给 {@link #topicIds}（两个至少有一个）。
     *
     * 原来这里是 {@code @NotNull}，现在放开是因为**一次可能要处理多条**。
     * 校验挪到服务端，两条路径同一个报错。
     */
    private Long topicId;

    /**
     * **多条**知识的 id（考目录时用）。
     *
     * 给了它就以它为准，{@link #topicId} 忽略。**归属在 SQL 里按 userId 收**，
     * 不是你的查不到 ⇒ 整批都拿不到 ⇒ 报"知识点不存在或已删除"。
     */
    private List<Long> topicIds;

    @NotBlank(message = "问题不能为空!")
    private String question;

    /** 用哪个接口调。为空时后端会明确报"还没选接口"，而不是随便挑一条 */
    private Long endpointId;

    /**
     * 引导问题的短名（`key:contexts`）。为空 = 用户自己敲的问题，
     * 那就只有正文，没有额外描述、也不附目录标签清单。
     */
    private String questionKey;

    /**
     * 这次要处理的**素材**：用户自己敲的一段文字。
     *
     * **为空就用这次指定的知识**（一条或多条，见 topicId / topicIds）。
     * 给了就用给的，**两者不同时出现**——两个素材会把模型绕晕，它分不清题目该考哪一份。
     *
     * 用途：出题不必被锁在这条知识里（可以拿一段书稿、一份资料出题）；
     * 考试判卷时用它把**整卷 + 标准答案 + 考生作答**一次带过去。
     * 上限 200000 字是给 body 兜的底，不是业务限制
     * （考一个目录时正文汇总能到十万字量级，先放开、以后要收再说）。
     */
    @Size(max = 200000, message = "素材太长了")
    private String material;

    /**
     * 之前几轮的问答，**由前端回传**。
     *
     * `/chat/completions` 是无状态协议：服务端什么都不存，没有会话 id 也没有"记得上次
     * 说了什么"。模型这一轮知道的全部就是这一次请求里的 messages 数组——**前端不回传，
     * 模型就真的不记得**，不是配置问题。
     *
     * 后端不落库也拿不到（问答记录本来就不存库），所以这份历史只能由抽屉自己交上来。
     * 为空 = 第一轮，行为和加历史之前完全一样。
     */
    private List<Turn> history;

    /** 一轮历史。question 是界面上显示的那句，answer 是模型当时的完整回答 */
    @Getter
    @Setter
    public static class Turn {

        private String question;

        private String answer;
    }
}