package com.mo.corecraft.controller.knowledge;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mo.corecraft.model.query.KpPaperQuery;
import com.mo.corecraft.model.req.KpAnswerSaveReq;
import com.mo.corecraft.model.req.KpAttemptCreateReq;
import com.mo.corecraft.model.req.KpAttributionSaveReq;
import com.mo.corecraft.model.req.KpGradeResultReq;
import com.mo.corecraft.model.req.KpPaperSaveReq;
import com.mo.corecraft.model.resp.KpAttemptResp;
import com.mo.corecraft.model.resp.KpAttemptReviewResp;
import com.mo.corecraft.model.resp.KpPaperResp;
import com.mo.corecraft.model.resp.KpPaperSummaryResp;
import com.mo.corecraft.model.resp.KpQuizCurrentResp;
import com.mo.corecraft.model.resp.ResultResp;
import com.mo.corecraft.service.KpExamAnswerService;
import com.mo.corecraft.service.KpExamAttemptService;
import com.mo.corecraft.service.KpExamPaperService;
import com.mo.corecraft.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 考试链路（设计 §11.2 / §11.3）：paper → attempt → answer → grade-result。
 * 存下来的卷子必须有入口能翻出来 ⇒ papers 列表 + attempts/{id}/answers 回看都在这里。
 */
@RestController
@RequestMapping("/kp/exam")
@RequiredArgsConstructor
public class KpExamController {

    private static final int MAX_PAGE_SIZE = 200;

    private final KpExamPaperService paperService;
    private final KpExamAttemptService attemptService;
    private final KpExamAnswerService answerService;

    /** 出卷完成后先持久化（题目快照 + 归属快照 + 范围快照），再进答题 */
    @RequestMapping(value = "papers", method = RequestMethod.POST)
    public ResultResp<Long> savePaper(@Validated @RequestBody KpPaperSaveReq req) {
        Long userId = SecurityUtil.getUserId();
        return ResultResp.data(paperService.savePaper(req, userId));
    }

    /** 考试历史列表：摘要（标题、类型、范围、题数、得分、状态、时间），不返回完整题干 */
    @RequestMapping(value = "papers", method = RequestMethod.GET)
    public ResultResp<IPage<KpPaperSummaryResp>> papers(KpPaperQuery query,
                                                        @RequestParam(defaultValue = "1") Integer page,
                                                        @RequestParam(defaultValue = "20") Integer size) {
        Long userId = SecurityUtil.getUserId();
        query.setUserId(userId);
        int limit = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int current = Math.max(page, 1);
        return ResultResp.data(paperService.selectPaperPage(Page.of(current, limit), query));
    }

    /** 试卷详情（含题目） */
    @RequestMapping(value = "papers/{id}", method = RequestMethod.GET)
    public ResultResp<KpPaperResp> paper(@PathVariable("id") Long id) {
        Long userId = SecurityUtil.getUserId();
        return ResultResp.data(paperService.selectPaper(id, userId));
    }

    /** 从考试历史删掉一份卷（软删：记录页 / 锚 / 回看不再出现，已产出的证据不动） */
    @RequestMapping(value = "papers/{id}", method = RequestMethod.DELETE)
    public ResultResp<Void> deletePaper(@PathVariable("id") Long id) {
        Long userId = SecurityUtil.getUserId();
        paperService.deletePaper(id, userId);
        return ResultResp.success();
    }

    /**
     * 随堂测验的锚（TODO-exam-lifecycle §三）：这条知识当前的卷 + 最新一次作答。
     * 点开测验先查它——在答的续答、考完的回看，不默认重新生成。
     * 「没有」是正常态（paper=null），不是 404。
     */
    @RequestMapping(value = "quiz-current", method = RequestMethod.GET)
    public ResultResp<KpQuizCurrentResp> quizCurrent(@RequestParam("topicId") Long topicId) {
        Long userId = SecurityUtil.getUserId();
        return ResultResp.data(paperService.selectQuizCurrent(topicId, userId));
    }

    /** 新建一次作答 */
    @RequestMapping(value = "papers/{id}/attempts", method = RequestMethod.POST)
    public ResultResp<Long> createAttempt(@PathVariable("id") Long id,
                                          @RequestBody KpAttemptCreateReq req) {
        Long userId = SecurityUtil.getUserId();
        if (req == null) {
            req = new KpAttemptCreateReq();
        }
        req.setPaperId(id);
        return ResultResp.data(attemptService.createAttempt(req, userId));
    }

    /** 按原卷重做：复用原卷题目，新建 attempt 指向原记录，原记录不动（设计 §11.3） */
    @RequestMapping(value = "attempts/{id}/replay", method = RequestMethod.POST)
    public ResultResp<Long> replay(@PathVariable("id") Long id) {
        Long userId = SecurityUtil.getUserId();
        KpAttemptCreateReq req = new KpAttemptCreateReq();
        req.setReplayOfAttemptId(id);
        // paperId 由服务端从原 attempt 取——不让调用方有机会拿两张不同的卷拼一次重做
        req.setPaperId(attemptService.selectAttempt(id, userId).getPaperId());
        return ResultResp.data(attemptService.createAttempt(req, userId));
    }

    @RequestMapping(value = "attempts/{id}", method = RequestMethod.GET)
    public ResultResp<KpAttemptResp> attempt(@PathVariable("id") Long id) {
        Long userId = SecurityUtil.getUserId();
        return ResultResp.data(attemptService.selectAttempt(id, userId));
    }

    /** 保存作答（自动保存 / 交卷前，PUT 全量覆盖） */
    @RequestMapping(value = "attempts/{id}/answers", method = RequestMethod.PUT)
    public ResultResp<Void> saveAnswers(@PathVariable("id") Long id,
                                        @Validated @RequestBody KpAnswerSaveReq req) {
        Long userId = SecurityUtil.getUserId();
        attemptService.saveAnswers(id, req, userId);
        return ResultResp.success();
    }

    /** 交卷：进入判分 */
    @RequestMapping(value = "attempts/{id}/submit", method = RequestMethod.POST)
    public ResultResp<Void> submit(@PathVariable("id") Long id) {
        Long userId = SecurityUtil.getUserId();
        attemptService.submit(id, userId);
        return ResultResp.success();
    }

    /** 判分结果回调：resultId 幂等；成绩、证据、学习投影、复习调度同事务提交 */
    @RequestMapping(value = "attempts/{id}/grade-result", method = RequestMethod.POST)
    public ResultResp<Void> gradeResult(@PathVariable("id") Long id,
                                        @Validated @RequestBody KpGradeResultReq req) {
        Long userId = SecurityUtil.getUserId();
        answerService.gradeResult(id, req, userId);
        return ResultResp.success();
    }

    /** 逐题回看：题干 + 你的作答 + 判定 + 得分 + 讲解 */
    @RequestMapping(value = "attempts/{id}/answers", method = RequestMethod.GET)
    public ResultResp<KpAttemptReviewResp> review(@PathVariable("id") Long id) {
        Long userId = SecurityUtil.getUserId();
        return ResultResp.data(answerService.selectReview(id, userId));
    }

    /** 保存题目的知识归属（出题后独立的弱归属一步；revision 服务端单调递增） */
    @RequestMapping(value = "questions/{id}/attribution", method = RequestMethod.POST)
    public ResultResp<Void> attribution(@PathVariable("id") Long id,
                                        @Validated @RequestBody KpAttributionSaveReq req) {
        Long userId = SecurityUtil.getUserId();
        answerService.saveAttribution(id, req, userId);
        return ResultResp.success();
    }
}
