package com.mo.corecraft.config.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String MDC_KEY = "traceId";
    /** JDK 25 ScopedValue：供同一请求/显式派生的虚拟线程安全读取当前 traceId */
    public static final ScopedValue<String> CURRENT = ScopedValue.newInstance();

    public static String currentTraceId() {
        return CURRENT.isBound() ? CURRENT.get() : MDC.get(MDC_KEY);
    }

    /** 在虚拟线程等子任务里沿用调用方的 traceId 上下文 */
    public static void runWithTraceId(String traceId, Runnable action) {
        if (!StringUtils.hasText(traceId)) {
            action.run();
            return;
        }
        MDC.put(MDC_KEY, traceId);
        try {
            ScopedValue.where(CURRENT, traceId).run(action);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (!StringUtils.hasText(traceId)) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        MDC.put(MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            ScopedValue.where(CURRENT, traceId).call(() -> {
                filterChain.doFilter(request, response);
                return null;
            });
        } catch (ServletException | IOException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
