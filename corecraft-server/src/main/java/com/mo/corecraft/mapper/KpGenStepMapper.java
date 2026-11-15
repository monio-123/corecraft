package com.mo.corecraft.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mo.corecraft.model.entity.KpGenStep;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 任务步骤（一次 AI 调用）。
 */
@Mapper
public interface KpGenStepMapper extends BaseMapper<KpGenStep> {

    /**
     * 只把正文写进去 —— **绕开 `KpTopicService#updateTopic`**。
     *
     * <p>为什么不复用 updateTopic：它对 `categoryId` 是**无条件覆盖写**
     * （配合 `updateStrategy = ALWAYS` 支持"移回未归类"）。服务端执行器手里只有
     * `topicId` 和正文，传 null 进去会把这条知识点**悄悄移回未归类**，
     * 而界面上一点异常都看不出来 —— 这种"生成完归类没了"的 bug 最难查。
     *
     * <p>所以这里写一条最小 UPDATE：只动 `content` 和内容版本锚点，别的列一个不碰。
     *
     * <p>⚠️ **`content_hash` / `content_epoch` 在这里必须跟着维护**（实施计划 §1.3）：
     * 正文有两条写入路径（用户编辑走 service、AI 生成走这里），只改一条 ⇒
     * 生成完 hash 不变 ⇒ epoch 判定永远不触发 ⇒「重大重写后待复测」是死的。
     * hash 由调用方用 {@code ContentHash} 算好传入（归一化口径只存那一处）；
     * {@code epochBump}：覆盖非空正文（重新生成）传 1，空占位首次生成传 0（设计 §9.2）。
     *
     * @param userId 归属（防止执行器拿错任务写到别人的知识点上）
     */
    @Update("UPDATE kp_topic SET content = #{content}, content_hash = #{contentHash}, "
            + "content_epoch = content_epoch + #{epochBump}, update_time = NOW() "
            + "WHERE id = #{id} AND user_id = #{userId} AND is_delete = 0")
    int updateTopicContent(@Param("id") Long id,
                           @Param("userId") Long userId,
                           @Param("content") String content,
                           @Param("contentHash") String contentHash,
                           @Param("epochBump") int epochBump);
}
