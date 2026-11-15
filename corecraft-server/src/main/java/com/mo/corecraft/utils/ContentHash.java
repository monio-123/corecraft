package com.mo.corecraft.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 内容指纹：**归一化 + SHA-256**。两个消费方同源（设计 §5.3 / §9.1）：
 * <ul>
 *   <li>{@code kp_topic.content_hash} —— 标题 + 正文的指纹，任何内容变化都更新；</li>
 *   <li>{@code kp_exam_question.question_fingerprint} —— 题干归一化哈希，跨卷撞车比对。</li>
 * </ul>
 *
 * <p><b>归一化口径只存这一处</b>：连续 ASCII 空白折叠成一个空格 + 去首尾空白。
 * 两端（知识点指纹、题目指纹）必须调这一个方法，散着写必有一处漂。
 *
 * <p>⚠️ 存量行的 content_hash 回填**不能**在 SQL 里 SHA2 一把：MySQL 的空白折叠语义
 * 和这里对不上（全角空格 / NBSP 的处理不同）⇒ 留 NULL，由写路径补算。
 */
public final class ContentHash {

    private ContentHash() {
    }

    /** 归一化：连续 ASCII 空白（空格 / \t / \n / \u000B / \f / \r）折叠成一个空格，再去首尾空白。 */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("[ \\t\\n\\x0B\\f\\r]+", " ").trim();
    }

    /** 标题 + 正文的知识点指纹（小写 hex SHA-256）。标题正文都过同一归一化。 */
    public static String ofTopic(String title, String content) {
        return sha256Hex(normalize(title) + "\n" + normalize(content));
    }

    /** 题干指纹（小写 hex SHA-256）。与 {@link #ofTopic} 同一套归一化 + 哈希。 */
    public static String ofQuestion(String questionText) {
        return sha256Hex(normalize(questionText));
    }

    private static String sha256Hex(String normalized) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // JDK 必带 SHA-256，走到这里说明运行环境坏了，不是业务异常
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
