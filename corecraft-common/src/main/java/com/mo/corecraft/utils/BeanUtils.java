package com.mo.corecraft.utils;

import org.springframework.cglib.beans.BeanCopier;
import org.springframework.cglib.core.Converter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class BeanUtils {

	/**
	 * BeanCopier **按 (源类, 目标类, 用不用 converter) 缓存**。
	 *
	 * <p><b>为什么</b>：{@code BeanCopier.create} 每调一次都要让 cglib **生成一个代理类**。
	 * 列表接口把 636 条实体转成 resp，就是 **636 个类**（实测 637 次共 68ms，
	 * 复用同一个 copier 之后是 0ms）。类越生成越多，Metaspace 也会被撑大。
	 *
	 * <p>copier 本身**没有状态**（它只认那对类的属性），所以复用是安全的。
	 * 建不出来（属性类型对不上）时照旧抛异常 —— 缓存只存成功的那次。
	 */
	private static final Map<String, BeanCopier> COPIERS = new ConcurrentHashMap<>();

	private static BeanCopier copier(Class<?> src, Class<?> dst, boolean useConverter) {
		String key = src.getName() + "->" + dst.getName() + (useConverter ? "#conv" : "");
		return COPIERS.computeIfAbsent(key, k -> BeanCopier.create(src, dst, useConverter));
	}

	public static void copy(Object src, Object target) {
		copy(src, target,null);
	}

	public static void copy(Object src, Object target, Converter converter) {
		copier(src.getClass(), target.getClass(), null!=converter).copy(src, target, converter);
	}

	public static <T, R> R createFrom(T source, Class<R> target){
		R targetObj;
		try{
			targetObj = target.getDeclaredConstructor().newInstance();
			copy(source, targetObj);
		}catch (Exception e) {
			throw new RuntimeException("copy error: " + e.getMessage());
		}
		return targetObj;
	}

	public static <T, R> void copyTo(T source, R target){
		try{
			copy(source, target);
		}catch (Exception e) {
			throw new RuntimeException("copy error");
		}
	}

	public static <T, R> Function<T, R> converter(Class<R> target){
		return source -> createFrom(source, target);
	}
}
