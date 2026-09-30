package com.mo.corecraft.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mo.corecraft.model.entity.KpTopicQuiz;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KpTopicQuizMapper extends BaseMapper<KpTopicQuiz> {

    // 物理删除：关联表是纯关系，不留墓碑（避免撞唯一键）
    @Delete("DELETE FROM kp_topic_quiz WHERE topic_id = #{topicId}")
    int physicalDeleteByTopicId(@Param("topicId") Long topicId);
}
