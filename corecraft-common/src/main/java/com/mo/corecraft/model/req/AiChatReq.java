package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 就某条知识提问。
 *
 * **只有 topicId + question + endpointId，没有 content**：正文由服务端按 topicId 自己从库里捞。
 * 前端传正文会带来两个问题——body 可能大到几 MB，以及"客户端能指定把哪段文本当上下文"
 * 等于给了绕过数据边界的一条路。
 *
 * endpointId 由前端传（当前选中哪个接口存在 localStorage 里），**不接受 userId**：
 * 传了就能用别人的密钥提问。
 */
@Getter
@Setter
public class AiChatReq {

    @NotNull(message = "知识点ID不能为空!")
    private Long topicId;

    @NotBlank(message = "问题不能为空!")
    private String question;

    /** 用哪个接口调。为空时后端会明确报"还没选接口"，而不是随便挑一条 */
    private Long endpointId;

    /**
     * 引导问题的字典 value（`key:contexts`）。为空 = 用户自己敲的问题，
     * 那就只有正文，没有额外描述、也不附目录标签清单。
     */
    private String questionKey;

    /**
     * 问答场景，决定用哪一段统一描述（字典 {@code ai_system_prompt} 的 value）。
     *
     * **必填、不给默认值**：场景写错就是"用了别人的描述去答"，而那种错答看着完全正常。
     * 查不到就抛，别退回某一段固定的描述。
     *
     * 现在只有一个 {@code chat}（就一条知识提问，5 个引导问题共用它）。将来加场景 =
     * 字典加一行 + 前端多传一个值，**后端不用改**（后端只认"带几条知识"那几种形状，
     * 形状没变就不用动代码）。真要引入新形状（零条、多条）那才得发版。
     */
    @NotBlank(message = "问答场景不能为空!")
    private String scene;

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