package com.mo.corecraft.utils;

import org.springframework.cglib.beans.BeanCopier;
import org.springframework.cglib.core.Converter;

public class BeanUtils {

	public static void copy(Object src, Object target) {
		copy(src, target,null);
	}
	
	public static void copy(Object src, Object target, Converter converter) {
		BeanCopier copier = BeanCopier.create(src.getClass(), target.getClass(), null!=converter);
		copier.copy(src, target, converter);
	}

	public static <T, R> R createFrom(T source, Class<R> target){
		R targetObj;
		try{
			targetObj = target.getDeclaredConstructor().newInstance();
			copy(source, targetObj);
		}catch (Exception e){
			throw new RuntimeException("copy error: " + e.getMessage());
		}
		return targetObj;
	}

	public static <T, R> void copyTo(T source, R target){
		try{
			copy(source, target);
		}catch (Exception e){
			throw new RuntimeException("copy error");
		}
	}
}
