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
	
	private static ObjectMapper getObjectMapper() {
        return SpringContextHolder.getBean(ObjectMapper.class);
    }
	
	private JsonUtils() {
		
	}
}
