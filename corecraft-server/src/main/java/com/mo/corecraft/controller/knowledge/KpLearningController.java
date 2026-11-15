package com.mo.corecraft.controller.knowledge;

import com.mo.corecraft.model.req.KpLearningMarkReq;
import com.mo.corecraft.model.resp.KpCategoryLearningResp;
import com.mo.corecraft.model.resp.KpEvidenceResp;
import com.mo.corecraft.model.resp.KpLearningResp;
import com.mo.corecraft.model.resp.KpTopicLevelResp;
import com.mo.corecraft.model.resp.ResultResp;
import com.mo.corecraft.service.KpMasteryEvidenceService;
import com.mo.corecraft.service.KpTopicLearningService;
import com.mo.corecraft.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 学习状态接口族（实施计划拍板 #1）：**统一走 {@code /kp/learning/**}**，
 * 含「标记已学」——不挂 {@code /kp/topic/{id}/learning}，知识点控制器不当杂物袋。
 */
@RestController
@RequestMapping("/kp/learning")
@RequiredArgsConstructor
public class KpLearningController {

    private final KpTopicLearningService learningService;
    private final KpMasteryEvidenceService masteryEvidenceService;

    /** 标记 / 取消已学（显式操作，不自动猜；空正文不能标记） */
    @RequestMapping(value = "topic/{id}/learning", method = RequestMethod.PUT)
    public ResultResp<Void> markLearning(@PathVariable("id") Long id,
                                         @Validated @RequestBody KpLearningMarkReq req) {
        Long userId = SecurityUtil.getUserId();
        learningService.markLearning(id, req, userId);
        return ResultResp.success();
    }

    /** 学习投影：能力掌握（A + 可信度）与记忆新鲜度（R）分两个维度返回 */
    @RequestMapping(value = "topic/{id}", method = RequestMethod.GET)
    public ResultResp<KpLearningResp> learning(@PathVariable("id") Long id) {
        Long userId = SecurityUtil.getUserId();
        return ResultResp.data(learningService.selectLearning(id, userId));
    }

    /** 该知识点的掌握度证据列表（回看；被替代的留痕可见） */
    @RequestMapping(value = "topic/{id}/evidence", method = RequestMethod.GET)
    public ResultResp<List<KpEvidenceResp>> evidence(@PathVariable("id") Long id) {
        Long userId = SecurityUtil.getUserId();
        return ResultResp.data(learningService.selectEvidence(id, userId));
    }

    /** 目录三指标聚合（设计 §8.1）：一次批量返回整棵树的聚合，不让前端逐目录请求 */
    @RequestMapping(value = "tree", method = RequestMethod.GET)
    public ResultResp<List<KpCategoryLearningResp>> tree(
            @RequestParam(value = "rootCategoryId", required = false) Long rootCategoryId) {
        Long userId = SecurityUtil.getUserId();
        return ResultResp.data(learningService.selectTree(rootCategoryId, userId));
    }

    /** 全部有评估的知识点的档位与掌握度值（目录树知识点行的圆点染色 + 进度条数据源） */
    @RequestMapping(value = "levels", method = RequestMethod.GET)
    public ResultResp<List<KpTopicLevelResp>> levels() {
        Long userId = SecurityUtil.getUserId();
        return ResultResp.data(learningService.selectMasteryLevels(userId));
    }

    /**
     * 重放本人的全部学习投影（幂等）。
     *
     * <p>「有证据即已学」上线后，2026-11-12 之前建的投影 {@code learned_at} 全是 NULL，
     * 需要重放一次才能补上（否则目录"已学数"永远是 0、复习永远不排期）。
     * 幂等：重复跑结果一致。
     */
    @RequestMapping(value = "recompute", method = RequestMethod.POST)
    public ResultResp<Integer> recompute() {
        Long userId = SecurityUtil.getUserId();
        return ResultResp.data(masteryEvidenceService.recomputeAllTopics(userId));
    }
}
