package com.mo.corecraft.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.enums.ResultCodeEnum;
import com.mo.corecraft.mapper.KpExamAttemptMapper;
import com.mo.corecraft.mapper.KpExamPaperMapper;
import com.mo.corecraft.mapper.KpExamQuestionMapper;
import com.mo.corecraft.model.entity.KpExamAttempt;
import com.mo.corecraft.model.entity.KpExamPaper;
import com.mo.corecraft.model.entity.KpExamQuestion;
import com.mo.corecraft.model.query.KpPaperQuery;
import com.mo.corecraft.model.req.KpPaperSaveReq;
import com.mo.corecraft.model.resp.KpPaperResp;
import com.mo.corecraft.model.resp.KpPaperSummaryResp;
import com.mo.corecraft.model.resp.KpQuestionResp;
import com.mo.corecraft.model.resp.KpQuizCurrentResp;
import com.mo.corecraft.service.KpExamPaperService;
import com.mo.corecraft.utils.ContentHash;
import com.mo.corecraft.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KpExamPaperServiceImpl implements KpExamPaperService {

    private final KpExamPaperMapper paperMapper;
    private final KpExamQuestionMapper questionMapper;
    private final KpExamAttemptMapper attemptMapper;

    @Override
    @Transactional
    public Long savePaper(KpPaperSaveReq req, Long userId) {
        KpExamPaper paper = new KpExamPaper();
        paper.setUserId(userId);
        paper.setKind(req.getKind());
        paper.setCategoryId(req.getCategoryId());
        paper.setTitle(req.getTitle() == null ? "" : req.getTitle());
        // 出卷链路跑完才来落库 ⇒ 直接 ready；生成失败的卷子走 failed（前端出卷失败时不调这里）
        paper.setStatus("ready");
        paper.setScopeSnapshot(JsonUtils.toJsonString(req.getScopeSnapshot()));
        paper.setSettingsSnapshot(JsonUtils.toJsonString(req.getSettingsSnapshot()));
        paperMapper.insert(paper);

        int no = 0;
        for (KpPaperSaveReq.KpQuestionSaveReq q : req.getQuestions()) {
            KpExamQuestion question = new KpExamQuestion();
            question.setPaperId(paper.getId());
            question.setQuestionNo(q.getQuestionNo() != null ? q.getQuestionNo() : ++no);
            question.setType(q.getType() == null ? "" : q.getType());
            question.setMaxScore(q.getMaxScore() == null ? BigDecimal.ZERO : q.getMaxScore());
            question.setQuestionPayload(JsonUtils.toJsonString(q.getQuestionPayload()));
            // 指纹只存哈希不存题干副本（设计 §5.3）；题干取 payload.stem，缺了退回整个 payload
            Object stem = q.getQuestionPayload() == null ? null : q.getQuestionPayload().get("stem");
            question.setQuestionFingerprint(ContentHash.ofQuestion(
                    stem != null ? String.valueOf(stem) : JsonUtils.toJsonString(q.getQuestionPayload())));
            question.setAttributionPayload(JsonUtils.toJsonString(q.getAttributionPayload()));
            boolean hasAttribution = q.getAttributionPayload() != null && !q.getAttributionPayload().isEmpty();
            question.setAttributionStatus(q.getAttributionStatus() != null ? q.getAttributionStatus()
                    : (hasAttribution ? "attributed" : "pending"));
            question.setAttributionRevision(hasAttribution ? 1 : 0);
            questionMapper.insert(question);
        }
        return paper.getId();
    }

    @Override
    public KpPaperResp selectPaper(Long paperId, Long userId) {
        KpExamPaper paper = ownedPaper(paperId, userId);
        KpPaperResp resp = new KpPaperResp();
        resp.setId(paper.getId());
        resp.setKind(paper.getKind());
        resp.setCategoryId(paper.getCategoryId());
        resp.setTitle(paper.getTitle());
        resp.setStatus(paper.getStatus());
        resp.setScopeSnapshot(JsonUtils.toStringMap(paper.getScopeSnapshot()));
        resp.setSettingsSnapshot(JsonUtils.toStringMap(paper.getSettingsSnapshot()));
        resp.setCreateTime(paper.getCreateTime());
        List<KpQuestionResp> questions = questionMapper.selectList(Wrappers.<KpExamQuestion>lambdaQuery()
                        .eq(KpExamQuestion::getPaperId, paperId)
                        .orderByAsc(KpExamQuestion::getQuestionNo)
                        .orderByAsc(KpExamQuestion::getId)).stream()
                .map(q -> {
                    KpQuestionResp qr = new KpQuestionResp();
                    qr.setId(q.getId());
                    qr.setPaperId(q.getPaperId());
                    qr.setQuestionNo(q.getQuestionNo());
                    qr.setType(q.getType());
                    qr.setMaxScore(q.getMaxScore());
                    qr.setQuestionPayload(JsonUtils.toStringMap(q.getQuestionPayload()));
                    qr.setAttributionStatus(q.getAttributionStatus());
                    return qr;
                })
                .toList();
        resp.setQuestions(questions);
        return resp;
    }

    @Override
    public IPage<KpPaperSummaryResp> selectPaperPage(Page<?> page, KpPaperQuery query) {
        LambdaQueryWrapper<KpExamPaper> wrapper = Wrappers.<KpExamPaper>lambdaQuery()
                .eq(KpExamPaper::getUserId, query.getUserId())
                .eq(query.getKind() != null, KpExamPaper::getKind, query.getKind())
                // 目录 = 整棵子树（前端展开好传进来的，口径见 KpPaperQuery#categoryIds）
                .in(query.getCategoryIds() != null && !query.getCategoryIds().isEmpty(),
                        KpExamPaper::getCategoryId, query.getCategoryIds())
                .eq(query.getStatus() != null, KpExamPaper::getStatus, query.getStatus())
                .ge(query.getCreatedFrom() != null, KpExamPaper::getCreateTime, query.getCreatedFrom())
                .le(query.getCreatedTo() != null, KpExamPaper::getCreateTime, query.getCreatedTo())
                // 「有作答还没交」（设置面板的未交卷提示）：子查询只认 answering——
                // grading/failed 是判分没跑完（去记录页重试判分），不是「没交」。
                // 卷已按 user_id 过滤、attempt 只能是本人的（createAttempt 校验过卷归属），子查询不用再带 user_id。
                .exists(Boolean.TRUE.equals(query.getUnfinished()),
                        "select 1 from kp_exam_attempt a where a.paper_id = kp_exam_paper.id and a.status = 'answering'")
                .orderByDesc(KpExamPaper::getId);
        // Page<?> 无法直接喂 selectPage（P 需与 T 同型），内部重建同游标的强类型分页对象
        Page<KpExamPaper> typedPage = new Page<>(page.getCurrent(), page.getSize());
        Page<KpExamPaper> paperPage = paperMapper.selectPage(typedPage, wrapper);
        List<KpExamPaper> papers = paperPage.getRecords();
        if (papers.isEmpty()) {
            Page<KpPaperSummaryResp> empty = new Page<>(paperPage.getCurrent(), paperPage.getSize(), 0);
            empty.setRecords(List.of());
            return empty;
        }

        List<Long> paperIds = papers.stream().map(KpExamPaper::getId).toList();
        Map<Long, Long> questionCounts = questionMapper.selectList(Wrappers.<KpExamQuestion>lambdaQuery()
                        .select(KpExamQuestion::getPaperId)
                        .in(KpExamQuestion::getPaperId, paperIds)).stream()
                .collect(Collectors.groupingBy(KpExamQuestion::getPaperId, Collectors.counting()));
        // 最近一次作答：按 paper 聚合出 max(id)（同卷多次作答/重做，取最新一条）
        Map<Long, KpExamAttempt> lastAttempts = attemptMapper.selectList(Wrappers.<KpExamAttempt>lambdaQuery()
                        .in(KpExamAttempt::getPaperId, paperIds)
                        .orderByDesc(KpExamAttempt::getId)).stream()
                .collect(Collectors.toMap(KpExamAttempt::getPaperId, Function.identity(), (a, b) -> a));

        List<KpPaperSummaryResp> records = new ArrayList<>();
        for (KpExamPaper paper : papers) {
            records.add(toSummary(paper, questionCounts.getOrDefault(paper.getId(), 0L).intValue(),
                    lastAttempts.get(paper.getId())));
        }
        Page<KpPaperSummaryResp> result = new Page<>(paperPage.getCurrent(), paperPage.getSize(), paperPage.getTotal());
        result.setRecords(records);
        return result;
    }

    @Override
    public KpQuizCurrentResp selectQuizCurrent(Long topicId, Long userId) {
        KpQuizCurrentResp resp = new KpQuizCurrentResp();
        if (topicId == null) {
            return resp;
        }
        // 锚的判定（TODO-exam-lifecycle §二）：kind=quiz 且 scope.topicIds 是单元素且就是这条，
        // 取最新一张。scope 匹配走 JSON 函数——写死序列化格式（LIKE '%"topicIds":[123]%'）
        // 在 Jackson 换个空格策略时就全失效，且界面上什么异常都看不到。
        // json_contains 的语义是「包含」：topicIds=[123,456] 也包含 [123] ⇒ 必须
        // 再钉一条 json_length=1（随堂测验的 scope 本来就只有当前这一条）。
        KpExamPaper paper = paperMapper.selectOne(Wrappers.<KpExamPaper>lambdaQuery()
                .eq(KpExamPaper::getUserId, userId)
                .eq(KpExamPaper::getKind, "quiz")
                .eq(KpExamPaper::getStatus, "ready")
                .apply("json_contains(scope_snapshot, cast({0} as json), '$.topicIds')" +
                        " and json_length(json_extract(scope_snapshot, '$.topicIds')) = 1",
                        "[" + topicId + "]")
                .orderByDesc(KpExamPaper::getId)
                .last("limit 1"));
        if (paper == null) {
            return resp;
        }
        int questionCount = Math.toIntExact(questionMapper.selectCount(Wrappers.<KpExamQuestion>lambdaQuery()
                .eq(KpExamQuestion::getPaperId, paper.getId())));
        KpExamAttempt last = attemptMapper.selectOne(Wrappers.<KpExamAttempt>lambdaQuery()
                .eq(KpExamAttempt::getPaperId, paper.getId())
                .orderByDesc(KpExamAttempt::getId)
                .last("limit 1"));
        resp.setPaper(toSummary(paper, questionCount, last));
        if (last != null) {
            resp.setAttempt(KpQuizCurrentResp.Attempt.of(last.getId(), last.getStatus()));
        }
        return resp;
    }

    /** paper + 题数 + 最新作答 → 列表摘要（分页列表与 quiz-current 共用一份映射） */
    private static KpPaperSummaryResp toSummary(KpExamPaper paper, int questionCount, KpExamAttempt last) {
        KpPaperSummaryResp s = new KpPaperSummaryResp();
        s.setId(paper.getId());
        s.setKind(paper.getKind());
        s.setCategoryId(paper.getCategoryId());
        s.setTitle(paper.getTitle());
        s.setStatus(paper.getStatus());
        s.setQuestionCount(questionCount);
        if (last != null) {
            s.setLastAttemptId(last.getId());
            s.setLastScore(last.getScore());
            s.setLastMaxScore(last.getMaxScore());
            s.setLastSubmittedAt(last.getSubmittedAt());
        }
        s.setCreateTime(paper.getCreateTime());
        return s;
    }

    @Override
    public void deletePaper(Long paperId, Long userId) {
        // ownedPaper 走 selectById（@TableLogic 自动带 is_delete=0）⇒ 已删卷再删是 404
        KpExamPaper paper = ownedPaper(paperId, userId);
        // BaseEntity 的 @TableLogic 把 deleteById 翻译成 UPDATE is_delete=1：
        // 记录页 / 锚 / 回看都不再出现；已产出的判定与证据不动（掌握度 §5「证据不可变」）。
        // 删除的锚卷不影响锚语义——quiz-current 按最新一张继续推导。
        paperMapper.deleteById(paper.getId());
    }

    private KpExamPaper ownedPaper(Long paperId, Long userId) {
        KpExamPaper paper = paperMapper.selectById(paperId);
        // 归属校验：试卷是用户私有数据（含标准答案），不是你的根本查不出来
        if (paper == null || !Objects.equals(paper.getUserId(), userId)) {
            throw new CoreCraftException(ResultCodeEnum.NOT_FOUND, "试卷不存在");
        }
        return paper;
    }
}
