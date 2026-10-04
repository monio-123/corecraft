package com.mo.corecraft.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mo.corecraft.model.entity.KpCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface KpCategoryMapper extends BaseMapper<KpCategory> {

    /**
     * 删除目录时，把挂在它下面的知识点置为"未归类"（category_id = NULL）。
     * <p>
     * 知识点绝不随目录一起删——目录只是归类的壳，整理结构不该丢知识。
     */
    @Update("UPDATE kp_topic SET category_id = NULL WHERE category_id = #{categoryId}")
    int clearTopicCategory(@Param("categoryId") Long categoryId);
}
