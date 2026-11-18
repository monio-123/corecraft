package com.mo.corecraft.utils;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.mo.corecraft.model.DateTimeFormats;
import tools.jackson.core.JacksonException;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ser.std.SimpleFilterProvider;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JsonUtils {

    private final static JsonMapper defaultMapper = initializeBaseMapper();

	public static JsonMapper.Builder baseMapperBuilder() {
		return JsonMapper.builder()
				//反序列化时未知字段忽略
				.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
				.configure(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
				.configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
				.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
				.configure(JsonReadFeature.ALLOW_JAVA_COMMENTS, true)
				//是否允许JSON字符串包含非引号控制字符
				.configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS, true)
				.changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(Include.NON_NULL))
				.filterProvider(new SimpleFilterProvider().setFailOnUnknownId(false))
				// 支持对于Date格式的反序列化
				.defaultDateFormat(new SimpleDateFormat(DateTimeFormats.DATETIME));
	}

	public static JsonMapper initializeBaseMapper() {
		return baseMapperBuilder().build();
	}
	 
	public static String toJsonString(Object obj) {
		try {
			return getObjectMapper().writeValueAsString(obj);
		} catch (JacksonException e) {
			throw new RuntimeException(e.getMessage(),e);
		}
	}

	public static String toJsonString(ObjectMapper objectMapper, Object obj) {
		try {
			return objectMapper.writeValueAsString(obj);
		} catch (JacksonException e) {
			throw new RuntimeException(e.getMessage(),e);
		}
	}
	
	public static <T> T[] toArrayObject(String json, Class<T> arrayTypeCls) {
		 try {
	            return null==json
	                ? null : getObjectMapper().readValue(json,
	                getObjectMapper().getTypeFactory().constructArrayType(arrayTypeCls));
	        } catch (JacksonException e) {
	        	throw new RuntimeException(e.getMessage(),e);
	        }
	}
	
	public static <T> List<T> toListObject(String json, Class<T> clazz) {
        try {
            return null==json
                ? null : getObjectMapper().readValue(json,
                getObjectMapper().getTypeFactory().constructParametricType(List.class, clazz));
        } catch (JacksonException e) {
        	throw new RuntimeException(e.getMessage(),e);
        }
    }
	
	public static <T> List<T> toListObject(InputStream in, Class<T> clazz) {
		try {
			return null==in
			        ? null : getObjectMapper().readValue(in,
			        getObjectMapper().getTypeFactory().constructParametricType(List.class, clazz));
		} catch (JacksonException e) {
			throw new RuntimeException(e.getMessage(),e);
		}
    }

	public static <T> T parseObject(String json, TypeReference<T> valueTypeRef) {
		try {
			return getObjectMapper().readValue(json, valueTypeRef);
		} catch (JacksonException e) {
			throw new RuntimeException(e.getMessage(),e);
		}
	}
	
	public static <T> T parseObject(InputStream in,Class<T> cls) {
		try {
			return getObjectMapper().readValue(in, cls);
		} catch (JacksonException e) {
			throw new RuntimeException(e.getMessage(),e);
		}
	}
	
	public static <T> T parseObject(String json,Class<T> cls) {
		try {
			return getObjectMapper().readValue(json, cls);
		} catch (JacksonException e) {
			throw new RuntimeException(e.getMessage(),e);
		}
	}

	public static JsonNode readTree(String json) {
		try {
			return getObjectMapper().readTree(json);
		} catch (JacksonException e) {
			throw new RuntimeException(e.getMessage(),e);
		}
	}
	
	public static Map parseObjectMap(String json) {
		try {
			return getObjectMapper().readValue(json, HashMap.class);
		} catch (JacksonException e) {
			throw new RuntimeException(e.getMessage(),e);
		}
	}

	/**
	 * JSON 字符串 → {@code Map<String, Object>}；**空 / 空白 / 不是 JSON 对象 ⇒ null**。
	 *
	 * <p>给"快照类"字段（试卷的范围/设置快照、作答 payload）一个统一读法——
	 * 原来各 service 里各写一份同名 {@code toMap}（readTree → toJava → instanceof Map），
	 * 两份就会各自漂（2026-11-15 收敛）。
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> toStringMap(String json) {
		if (json == null || json.isBlank()) {
			return null;
		}
		Object v = toJava(readTree(json));
		return v instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
	}

	/**
	 * 取一个**字符串字段**。
	 *
	 * <p>**没有这个字段 / 字段是 null ⇒ 返回 null**（不给空串）——
	 * 调用方自己决定"没有"要当成什么（空串 / 跳过 / 报错）。数字、布尔这类非字符串节点
	 * 按字面量取（{@code asText()}），因为上游给的是数字时那也是值。
	 *
	 * <p>⚠️ **原来 {@code AiChatServiceImpl} 和 {@code AiVendorServiceImpl} 各写了一份同名同形状的
	 * {@code text()}，语义还不一样**（一个没字段给空串、一个给 null）⇒ 收敛到这一处。
	 */
	public static String text(JsonNode node, String field) {
		if (node == null) {
			return null;
		}
		JsonNode v = node.get(field);
		if (v == null || v.isNull()) {
			return null;
		}
		return v.asText();
	}

	/**
	 * JsonNode → 普通 Java 对象（Map / List / 字符串 / 数字 / 布尔）。
	 *
	 * <p>**用途：同一段 JSON 只解析一次**。原来"先 readTree 校验是不是对象、再 parseObjectMap 取值"
	 * 是把同一段 JSON 解析了两遍；现在校验和取值共用这一个节点。
	 */
	public static Object toJava(JsonNode node) {
		if (node == null || node.isNull()) {
			return null;
		}
		return getObjectMapper().convertValue(node, Object.class);
	}
	
	/**
	 * **本工具用的那份 mapper**。
	 *
	 * <p>⚠️ **它不再是"容器里那份"** —— 原来这里去 {@code SpringContextHolder}
	 * 取 Boot 的 mapper，而 {@link #defaultMapper} 建好就没人读（"死配置 + 意图未落地"）。
	 * 现在**自己那份就是权威那份**（2026-11-05 用户拍板：「让它真正生效」）。
	 *
	 * <p>⚠️ **HTTP 响应不走这里**（Spring MVC 用自己的 HttpMessageConverter）⇒
	 * 改它**不会**改变接口返回的 JSON 形状。它只影响这四处：
	 * 缓存 key、**发给厂商的 AI 请求体**、{@code ai_endpoint} 的 params/custom 落库字符串。
	 */
	private static ObjectMapper getObjectMapper() {
        return defaultMapper;
    }
	
	private JsonUtils() {
		
	}
}
