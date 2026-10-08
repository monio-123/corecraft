package com.mo.corecraft.utils;

import jakarta.annotation.PreDestroy;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 外部 HTTP 调用的统一出口。
 *
 * 从 tydic 那套 HttpExecutor 搬过来的，**砍掉了三样东西**——都不是"以后可能用得上"，
 * 是判断它们当前没有使用者、而且带着隐患：
 *
 * ① insecureClient（TrustAllStrategy + NoopHostnameVerifier）：完全不验证书。
 *    AI 调用要带 API Key 走这条路，等于把密钥明文发给任何能冒充该域名的中间人。
 *    内部可信服务用没问题，对外部供应商不行。项目当前也没有第二个使用者。
 * ② doGetBytes / doGetBytesWithHeaders / BytesResponse：零使用者。
 * ③ 4.x 的 org.apache.http.* API：换成 Spring Boot 3.2 统一管理的 httpclient5，
 *    javax.annotation.PreDestroy → jakarta.annotation.PreDestroy。
 *
 * **和原版唯一实质差异：多了 doPostStream。** 原版 doPost 走 EntityUtils.toString，
 * 会把整个响应读完才返回。调 LLM 的 stream:true 时响应体要挂着好几分钟等模型吐字，
 * 那样等于"一次性返回"，等整段话生成完才一次性吐给前端。流式必须拿到
 * InputStream 自己逐行读——原版没有任何方法能拿得到，这就是搬过来必须补的那个。
 */
@Component
public class HttpExecutor {

    private static final String CHARSET = StandardCharsets.UTF_8.name();

    private final CloseableHttpClient client;
    private final RequestConfig defaultConfig;
    /** 流式专用：response timeout 设 0（hc5 里 0 = 无限），默认那个会把回答掐到一半 */
    private final RequestConfig streamConfig;

    public HttpExecutor(@Value("${app.http.connect-timeout:5000}") int connectTimeout,
                        @Value("${app.http.response-timeout:60000}") int responseTimeout) {
        // hc5 把"连上要多久"从 RequestConfig 挪到了 ConnectionConfig，必须挂在连接管理器上。
        // RequestConfig 里那个 setConnectTimeout 还在，但已废弃，不用它。
        this.client = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout))
                                .build())
                        .build())
                .build();

        this.defaultConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectTimeout))
                .setResponseTimeout(Timeout.ofMilliseconds(responseTimeout))
                .build();
        this.streamConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectTimeout))
                .setResponseTimeout(Timeout.ofSeconds(0))
                .build();
    }

    public String doGet(String url, Map<String, String> headers) throws IOException {
        HttpGet request = new HttpGet(url);
        applyHeaders(request, headers);
        request.setConfig(defaultConfig);
        return execute(request);
    }

    public String doPost(String url, Map<String, String> headers, String body) throws IOException {
        return execute(newPost(url, headers, body, defaultConfig));
    }

    /**
     * 流式 POST：逐行把上游响应体喂给 consumer，直到流结束。
     *
     * **不解析内容**，SSE / JSONL / 什么格式都行，具体怎么切由调用方决定——
     * 这是通用能力，不该在 HTTP 层塞进某个业务的协议知识。
     *
     * @param consumer 每读到一行调用一次；抛 IOException 会中断本次调用
     */
    public void doPostStream(String url, Map<String, String> headers, String body,
                             LineConsumer consumer) throws IOException {
        client.execute(newPost(url, headers, body, streamConfig), response -> {
            int status = response.getCode();
            if (status >= 400) {
                HttpEntity entity = response.getEntity();
                String detail = entity == null ? "" : EntityUtils.toString(entity, CHARSET);
                throw new IOException("上游返回 " + status + "：" + detail);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.getEntity().getContent(), CHARSET))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    consumer.accept(line);
                }
            }
            return null;
        });
    }

    @PreDestroy
    public void close() throws IOException {
        client.close();
    }

    // ==================== 内部 ====================

    private HttpPost newPost(String url, Map<String, String> headers, String body, RequestConfig config) {
        HttpPost request = new HttpPost(url);
        applyHeaders(request, headers);
        request.setConfig(config);
        if (body != null) {
            request.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
        }
        return request;
    }

    private String execute(HttpUriRequestBase request) throws IOException {
        return client.execute(request, response -> {
            HttpEntity entity = response.getEntity();
            return entity == null ? "" : EntityUtils.toString(entity, CHARSET);
        });
    }

    private static void applyHeaders(HttpUriRequestBase request, Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        headers.forEach(request::setHeader);
    }

    /** 一行响应。允许抛 IOException，让调用方能中断整条流。 */
    @FunctionalInterface
    public interface LineConsumer {
        void accept(String line) throws IOException;
    }
}