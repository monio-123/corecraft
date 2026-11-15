package com.mo.corecraft.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mo.corecraft.model.entity.KpGenTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 生成任务。普通 CRUD 全走 MyBatis-Plus，没有自定义 SQL。
 */
@Mapper
public interface KpGenTaskMapper extends BaseMapper<KpGenTask> {
}
