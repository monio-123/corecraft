package com.mo.corecraft.exception;

import java.io.IOException;

/**
 * 上游（AI 厂商等）返回的 HTTP 错误，**带状态码**。
 *
 * <p>为什么单独一个类型：4xx 和 5xx 的处理方式根本不同。
 * <ul>
 *   <li>**4xx（401/402/403/404…）= 确定性的**：密钥错、余额不足、模型没开通。
 *       再调一次结果一样 ⇒ 重试只是白烧额度和时间。</li>
 *   <li>**5xx / 超时 = 偶发的**：上游抖一下，重试有希望。</li>
 * </ul>
 *
 * <p>以前这里只抛一个裸 {@link IOException}（消息是「上游返回 402：{json}」），
 * 上层分不出「再试一次也许能成」和「再试一百次也是白搭」——
 * 结果就是批量任务里 100 条知识**各撞一次同一堵墙**（2026-11-11 实测：
 * 上游余额不足，100 条里 91 条以 402 失败，每条都真的发了请求）。
 *
 * <p>⇒ 有了状态码，上层才能**在第一条就停**，并把原因翻译成用户能动手的话。
 */
public class UpstreamHttpException extends IOException {

    /** 上游返回的 HTTP 状态码（400+）。 */
    private final int status;

    /** 上游响应体原文（可能是一整段 JSON，截断后给用户看）。 */
    private final String body;

    public UpstreamHttpException(int status, String body) {
        super("上游返回 " + status + "：" + body);
        this.status = status;
        this.body = body == null ? "" : body;
    }

    public int getStatus() {
        return status;
    }

    public String getBody() {
        return body;
    }

    /**
     * 是不是「重试也没用」的那一类。
     *
     * <p>**4xx 一律算**（4xx = 请求本身有问题，重发同一个请求还是同一个结果），
     * 但 **429 除外**：限流是「等一会儿再来」的意思，重试有意义。
     * 5xx 和超时都不算 —— 那是上游的问题，值得再试一次。
     */
    public boolean isDeterministic() {
        return status >= 400 && status < 500 && status != 429;
    }

    /**
     * 把上游的原始报错翻译成**用户能动手的话**。
     *
     * <p>只说事实、不说界面路径（brove-shared §8）。上游的原文保留在后面，
     * 用户想找客服时能直接给出去。
     */
    public String toUserMessage() {
        String lower = body.toLowerCase();
        if (status == 401) {
            return "API 密钥无效或已过期，请检查这条 API 的密钥";
        }
        if (status == 402) {
            return "上游账号余额不足，请先充值再试";
        }
        if (status == 403) {
            // 阿里云「模型没开通」和「账号状态异常」都返回 403，靠 body 里的 code 区分。
            if (lower.contains("unpurchased")) {
                return "这个模型没有开通权限，请换一个模型或联系供应商";
            }
            return "上游拒绝了这次调用（403），请检查账号权限或换一条 API";
        }
        if (status == 404) {
            return "上游找不到这个模型（404），请检查模型名是否写对";
        }
        if (status == 429) {
            return "请求太频繁被限流了，请稍后再试";
        }
        if (status >= 500) {
            return "上游服务出错（" + status + "），这是对方的问题，可以重试";
        }
        return "上游拒绝了这次调用（" + status + "）";
    }
}
