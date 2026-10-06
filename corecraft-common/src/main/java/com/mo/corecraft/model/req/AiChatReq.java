package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

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
}