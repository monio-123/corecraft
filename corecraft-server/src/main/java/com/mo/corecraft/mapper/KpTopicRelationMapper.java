package com.mo.corecraft.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mo.corecraft.model.entity.KpTopicRelation;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KpTopicRelationMapper extends BaseMapper<KpTopicRelation> {

    // 物理删除：关联表是纯关系，不留墓碑（避免撞唯一键）
    @Delete("DELETE FROM kp_topic_relation WHERE topic_id = #{topicId}")
    int physicalDeleteByTopicId(@Param("topicId") Long topicId);

    @Delete("DELETE FROM kp_topic_relation WHERE topic_id = #{topicId} AND related_topic_id = #{relatedTopicId}")
    int physicalDeleteByTopicIdAndRelatedTopicId(@Param("topicId") Long topicId, @Param("relatedTopicId") Long relatedTopicId);
}
