package com.mo.corecraft.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mo.corecraft.model.entity.KpTopic;
import com.mo.corecraft.model.resp.KpTopicIndexResp;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KpTopicMapper extends BaseMapper<KpTopic> {

    /**
     * 知识点的**结构索引**（轻量投影，**不含正文**）。
     *
     * <p>SQL 在 `mappers/KpTopicMapper.xml` 里 —— 为什么单独一条、为什么不查实体，
     * 那边的注释说全了。
     *
     * @param userId 归属（只查自己的）
     * @param q      关键词（标题 + 正文都搜）；空 = 不筛
     */
    List<KpTopicIndexResp> selectIndex(@Param("userId") Long userId, @Param("q") String q);
}
