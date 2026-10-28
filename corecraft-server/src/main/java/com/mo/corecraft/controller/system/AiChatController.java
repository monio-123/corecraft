package com.mo.corecraft.controller.system;

import com.mo.corecraft.model.req.AiChatReq;
import com.mo.corecraft.model.resp.AiQuestionResp;
import com.mo.corecraft.model.resp.ResultResp;
import com.mo.corecraft.service.AiChatService;
import com.mo.corecraft.utils.SecurityUtil;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 就一条知识提问（流式）。
 *
 * 路径 /ai/chat 而不塞进 /ai/config —— 那是配置，这是用配置。
 *
 * **userId 一律从登录态取**，和 AiConfigController 同一个规矩：传了就能问别人的知识。
 * **正文不由前端传**：只收 topicId，服务端自己从库里捞，body 短、也断掉"客户端能指定
 * 把哪段文本当上下文"这条路。
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiChatController {

    /**
     * 一次问答最多允许多久（毫秒）。
     *
     * <p>**做成配置**（{@code app.ai.chat-timeout-ms}，默认 30 分钟）：写死 5 分钟的时候，
     * "模型想得久 + 输出得长"（出卷、写正文、生成骨架）就会**静默截断** ——
     * 流直接结束，界面上看着"生成完了"，内容却少了一截。
     * SseEmitter 的超时是**从请求开始算**的（不是 idle 超时），模型思考 3 分钟 + 输出 3 分钟
     * 就已经超了。
     */
    @Value("${app.ai.chat-timeout-ms:1800000}")
    private long chatTimeoutMs;

    private final AiChatService aiChatService;

    /**
     * 抽屉空态那几个引导问题。来自配置树上的「引导问题」那组——改问题在管理后台的
     * 系统配置里改，不用改代码、也不用发版。
     */
    @GetMapping("/chat/questions")
    public ResultResp<List<AiQuestionResp>> questions() {
        return ResultResp.data(aiChatService.questions());
    }

    /**
     * SseEmitter 要在 handler 返回**之后**由别的线程往里写，所以调模型必须另起线程，
     * 否则请求线程会被占住几分钟。放在 controller 而不是 service：异步是 Web 层的事，
     * service 保持阻塞、纯业务。模型调用是低频的用户主动行为，cached pool 够用。
     */
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ai-chat");
        t.setDaemon(true);
        return t;
    });

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Validated @RequestBody AiChatReq req) {
        Long userId = SecurityUtil.getUserId();
        SseEmitter emitter = new SseEmitter(chatTimeoutMs);

        executor.execute(() -> {
            try {
                aiChatService.stream(userId, req, delta -> {
                    if (StringUtils.isNotEmpty(delta.prompt())) {
                        send(emitter, Map.of("prompt", delta.prompt()));
                    }
                    if (StringUtils.isNotEmpty(delta.reasoning())) {
                        send(emitter, Map.of("reasoning", delta.reasoning()));
                    }
                    if (StringUtils.isNotEmpty(delta.content())) {
                        send(emitter, Map.of("delta", delta.content()));
                    }
                    // **上游为什么停**（stop / length …）。前端靠它知道内容是不是**截断**的 ——
                    // 没有这个信息，撞了输出上限也看不出来（界面和库里都像"生成完了"）。
                    if (StringUtils.isNotEmpty(delta.finishReason())) {
                        send(emitter, Map.of("finish", delta.finishReason()));
                    }
                });
                send(emitter, Map.of("done", true));
            } catch (Exception e) {
                sendQuietly(emitter, Map.of("error", message(e)));
            } finally {
                emitter.complete();
            }
        });
        return emitter;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    // ==================== 内部 ====================

    private void send(SseEmitter emitter, Map<String, Object> payload) {
        try {
            emitter.send(SseEmitter.event().data(payload, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            // 用户关掉页面时 send 会失败。包成 unchecked 往上传，让整条流在这里停下——
            // 继续往下写只会一路抛 IOException。
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 发错误事件。**用户提前关掉页面时 send 会抛 IOException**——那时已经没有接收方了，
     * 再重试一次也只是再抛一遍，直接吞掉。不是掩盖故障，是"没人在听"时的正常收尾。
     */
    private void sendQuietly(SseEmitter emitter, Map<String, Object> payload) {
        try {
            send(emitter, payload);
        } catch (Exception ignored) {
            // 客户端已断开，见方法注释
        }
    }

    private String message(Exception e) {
        String msg = e.getMessage();
        return StringUtils.isBlank(msg) ? "AI 调用失败" : msg;
    }
}