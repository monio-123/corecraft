package com.mo.corecraft.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mo.corecraft.model.entity.KpCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;

@Mapper
public interface KpCategoryMapper extends BaseMapper<KpCategory> {

    /**
     * 删除目录时，把挂在这些目录下面的知识点置为"未归类"（category_id = NULL）。
     * <p>
     * 知识点绝不随目录一起删——目录只是归类的壳，整理结构不该丢知识。
     * <p>
     * **收一批 id 而不是一个**：2026-10-07 之前删除是"子目录上提"，所以只清直接下级。
     * 现在改成级联删除（子目录一并删），只清直接下级会让挂在**更深一层**的知识点留下
     * category_id 指向一个已经物理删掉的目录——那些知识在界面上查不到、数据却还在。
     * 一次 IN 把整棵子树清干净。
     */
    @Update("<script>"
            + "UPDATE kp_topic SET category_id = NULL WHERE category_id IN "
            + "<foreach collection='categoryIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    int clearTopicCategory(@Param("categoryIds") Collection<Long> categoryIds);
}
