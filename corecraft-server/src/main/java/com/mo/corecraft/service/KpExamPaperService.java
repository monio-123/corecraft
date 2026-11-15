package com.mo.corecraft.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mo.corecraft.model.query.KpPaperQuery;
import com.mo.corecraft.model.req.KpPaperSaveReq;
import com.mo.corecraft.model.resp.KpPaperResp;
import com.mo.corecraft.model.resp.KpPaperSummaryResp;
import com.mo.corecraft.model.resp.KpQuizCurrentResp;

/**
 * 试卷档案（设计 §10.2 / §11.3）：出卷后先持久化，再进答题；存下来的卷子有入口能翻出来。
 */
public interface KpExamPaperService {

    Long savePaper(KpPaperSaveReq req, Long userId);

    KpPaperResp selectPaper(Long paperId, Long userId);

    IPage<KpPaperSummaryResp> selectPaperPage(Page<?> page, KpPaperQuery query);

    /**
     * 随堂测验的锚（TODO-exam-lifecycle §三）：这条知识当前的卷 + 最新一次作答。
     * 「当前」= kind=quiz 且 scope 就是这一条（单元素）里最新一张——**可推导不物化**
     * （建绑定表就是第二份真相，两边漂移没人说得清哪边对）。
     */
    KpQuizCurrentResp selectQuizCurrent(Long topicId, Long userId);

    /**
     * 从考试历史里删掉一份卷（软删 {@code is_delete=1}，DDL 注释预留的口径）：
     * 记录页 / 锚 / 回看都不再出现；**已产出的证据不动**（掌握度 §5「证据不可变」）——
     * 删的是"入口"，不是历史事实。
     */
    void deletePaper(Long paperId, Long userId);
}
