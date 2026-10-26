package com.mo.corecraft.utils;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JsonUtils {

	@Getter
    private final static ObjectMapper defaultMapper = initializeBaseMapper();

	public static ObjectMapper initializeBaseMapper() {
	        ObjectMapper mapper = new ObjectMapper()
	            //反序列化时未知字段忽略
	            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
	            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true)
	            .configure(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
	            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
	            .configure(Feature.ALLOW_COMMENTS, true)
	            .registerModule(new JavaTimeModule())
				// 使用 ISO 字符串序列化 LocalDateTime
				.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
	            .registerModule(new Jdk8Module())
	            .setSerializationInclusion(Include.NON_NULL)
	            .setFilterProvider(new SimpleFilterProvider().setFailOnUnknownId(false))
				// 支持对于Date格式的反序列化
				.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

	        DeserializationConfig readConfig = mapper.getDeserializationConfig()
	            //是否允许JSON字符串包含非引号控制字符
	            .with(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS);
	        return mapper.setConfig(readConfig);
	    }
	 
	public static String toJsonString(Object obj) {
		try {
			return getObjectMapper().writeValueAsString(obj);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e.getMessage(),e);
		}
	}

	public static String toJsonString(ObjectMapper objectMapper, Object obj) {
		try {
			return objectMapper.writeValueAsString(obj);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e.getMessage(),e);
		}
	}
	
	public static <T> T[] toArrayObject(String json, Class<T> arrayTypeCls) {
		 try {
	            return null==json
	                ? null : getObjectMapper().readValue(json,
	                getObjectMapper().getTypeFactory().constructArrayType(arrayTypeCls));
	        } catch (JsonProcessingException e) {
	        	throw new RuntimeException(e.getMessage(),e);
	        }
	}
	
	public static <T> List<T> toListObject(String json, Class<T> clazz) {
        try {
            return null==json
                ? null : getObjectMapper().readValue(json,
                getObjectMapper().getTypeFactory().constructParametricType(List.class, clazz));
        } catch (JsonProcessingException e) {
        	throw new RuntimeException(e.getMessage(),e);
        }
    }
	
	public static <T> List<T> toListObject(InputStream in, Class<T> clazz) {
		try {
			return null==in
			        ? null : getObjectMapper().readValue(in,
			        getObjectMapper().getTypeFactory().constructParametricType(List.class, clazz));
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage(),e);
		}
    }

	public static <T> T parseObject(String json, TypeReference<T> valueTypeRef) {
		try {
			return getObjectMapper().readValue(json, valueTypeRef);
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage(),e);
		}
	}
	
	public static <T> T parseObject(InputStream in,Class<T> cls) {
		try {
			return getObjectMapper().readValue(in, cls);
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage(),e);
		}
	}
	
	public static <T> T parseObject(String json,Class<T> cls) {
		try {
			return getObjectMapper().readValue(json, cls);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e.getMessage(),e);
		}
	}

	public static JsonNode readTree(String json) {
		try {
			return getObjectMapper().readTree(json);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e.getMessage(),e);
		}
	}
	
	public static Map parseObjectMap(String json) {
		try {
			return getObjectMapper().readValue(json, HashMap.class);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e.getMessage(),e);
		}
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
	
	private static ObjectMapper getObjectMapper() {
        return SpringContextHolder.getBean(ObjectMapper.class);
    }
	
	private JsonUtils() {
		
	}
}
