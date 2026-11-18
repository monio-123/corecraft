package com.mo.corecraft.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mo.corecraft.model.entity.KpTopicTag;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KpTopicTagMapper extends BaseMapper<KpTopicTag> {

    // 物理删除（不走 @TableLogic）：关联表是纯关系，关系断了不留墓碑
    // 否则重新插 (topic_id, tag_id) 会撞唯一键 uk_topic_tag
    @Delete("DELETE FROM kp_topic_tag WHERE topic_id = #{topicId}")
    int physicalDeleteByTopicId(@Param("topicId") Long topicId);

    @Delete({
            "<script>",
            "DELETE FROM kp_topic_tag WHERE topic_id IN",
            "<foreach collection='topicIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    int physicalDeleteByTopicIds(@Param("topicIds") List<Long> topicIds);

    @Delete("DELETE FROM kp_topic_tag WHERE tag_id = #{tagId}")
    int physicalDeleteByTagId(@Param("tagId") Long tagId);
}
