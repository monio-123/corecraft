package com.mo.corecraft.service.support;

import tools.jackson.databind.JsonNode;
import com.mo.corecraft.model.entity.KpGenTask;
import com.mo.corecraft.model.req.AiChatReq;
import com.mo.corecraft.service.AiChatService;
import com.mo.corecraft.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * 后台任务模型调用器：封装流式收包、参数快照读取与 {@code length} 截断涨额度重试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenTaskAiCaller {

    public record CallResult(String text, String finish, String prompt,
                             Integer tokenPrompt, Integer tokenCompletion, int durationMs) {
    }

    public record LengthCall(CallResult result, boolean retried) {
    }

    private final AiChatService aiChatService;
    private final GenTaskStore store;

    public Long endpointIdOf(KpGenTask task) {
        String raw = store.paramsField(task.getParams(), "endpointId");
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public LengthCall callWithLengthRetry(KpGenTask task, String material, String questionKey,
                                            String logLabel) throws IOException {
        CallResult r = callOnce(task, material, null, questionKey);
        boolean retried = false;
        if ("length".equals(r.finish())) {
            log.info("{}撞输出上限，涨额度重试", logLabel);
            CallResult again = callOnce(task, material, GenTaskStore.RETRY_MAX_TOKENS, questionKey);
            if (StringUtils.hasText(again.text())) {
                r = again;
                retried = true;
            }
        }
        return new LengthCall(r, retried);
    }

    public CallResult callOnce(KpGenTask task, String material, Integer maxTokens, String questionKey) throws IOException {
        AiChatReq req = new AiChatReq();
        req.setQuestion(questionText(questionKey));
        req.setMaterial(material);
        req.setQuestionKey(questionKey);
        applyParams(task, req);
        if (maxTokens != null) {
            req.setMaxTokens(maxTokens);
        }

        StringBuilder body = new StringBuilder();
        String[] prompt = new String[1];
        String[] finish = new String[1];
        Integer[] tokens = new Integer[2];
        long t0 = System.currentTimeMillis();

        aiChatService.stream(task.getUserId(), req, delta -> {
            if (StringUtils.hasText(delta.prompt())) {
                prompt[0] = delta.prompt();
            }
            // 流式增量里的空格和换行会单独成帧，绝不能用 hasText 判断
            if (delta.content() != null && !delta.content().isEmpty()) {
                body.append(delta.content());
            }
            if (StringUtils.hasText(delta.finishReason())) {
                finish[0] = delta.finishReason();
            }
            if (delta.tokenPrompt() != null) {
                tokens[0] = delta.tokenPrompt();
            }
            if (delta.tokenCompletion() != null) {
                tokens[1] = delta.tokenCompletion();
            }
        });

        return new CallResult(body.toString(), finish[0], prompt[0],
                tokens[0], tokens[1], (int) (System.currentTimeMillis() - t0));
    }

    private void applyParams(KpGenTask task, AiChatReq req) {
        Long endpointId = endpointIdOf(task);
        if (endpointId != null) {
            req.setEndpointId(endpointId);
        }
        if (!StringUtils.hasText(task.getParams())) {
            return;
        }
        try {
            JsonNode p = JsonUtils.readTree(task.getParams());
            if (p.hasNonNull("maxTokens")) {
                req.setMaxTokens(p.get("maxTokens").asInt());
            }
        } catch (Exception e) {
            log.warn("任务 {} 的参数快照解析失败：{}", task.getId(), e.getMessage());
        }
    }

    private static String questionText(String questionKey) {
        if (GenTaskStore.Q_PLAN_SKELETON.equals(questionKey)) {
            return "生成目录骨架";
        }
        if (GenTaskStore.Q_PLAN_EXPAND.equals(questionKey)) {
            return "展开这一章";
        }
        if (GenTaskStore.Q_EXAM_PAPER.equals(questionKey)) {
            return "出题";
        }
        return GenTaskStore.NAME_QUESTION_KEY.equals(questionKey) ? "给这条内容起个标题" : "写知识点";
    }
}
