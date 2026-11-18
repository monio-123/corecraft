package com.mo.corecraft.service.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.exception.UpstreamHttpException;
import com.mo.corecraft.mapper.KpGenStepMapper;
import com.mo.corecraft.mapper.KpGenTaskMapper;
import com.mo.corecraft.model.entity.KpGenStep;
import com.mo.corecraft.model.entity.KpGenTask;
import com.mo.corecraft.model.req.KpGenTaskCreateReq;
import com.mo.corecraft.model.resp.KpGenTaskResp;
import com.mo.corecraft.utils.AppClock;
import com.mo.corecraft.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 生成任务与步骤的共用数据访问、列级状态更新及通用小工具。
 *
 * <p>集中维护状态常量（{@code ST_*} / {@code STEP_*}）、列级防覆盖更新（防止暂停失效）、
 * 节点链聚合与 {@code AppClock} 墙钟时间戳，供服务与各执行阶段共用。
 */
@Component
@RequiredArgsConstructor
public class GenTaskStore {

    public static final String NAME_QUESTION_KEY = "rename:none";
    public static final String Q_EXAM_PAPER = "ai.prompt.exam.paper";
    public static final String Q_POINT = "ai.prompt.knowledge.point";
    public static final String Q_PLAN_SKELETON = "ai.prompt.knowledge.plan.skeleton";
    public static final String Q_PLAN_EXPAND = "ai.prompt.knowledge.plan.expand";
    public static final int RETRY_MAX_TOKENS = 32768;

    public static final String ST_QUEUED = "queued";
    public static final String ST_RUNNING = "running";
    public static final String ST_SUCCEEDED = "succeeded";
    public static final String ST_FAILED = "failed";
    public static final String ST_CANCELING = "canceling";
    public static final String ST_CANCELED = "canceled";
    public static final String ST_AWAITING = "awaiting";

    public static final String STEP_PENDING = "pending";
    public static final String STEP_RUNNING = "running";
    public static final String STEP_OK = "ok";
    public static final String STEP_FAILED = "failed";
    public static final String STEP_SKIPPED = "skipped";

    private final KpGenTaskMapper taskMapper;
    private final KpGenStepMapper stepMapper;

    public KpGenTaskMapper tasks() {
        return taskMapper;
    }

    public KpGenStepMapper steps() {
        return stepMapper;
    }

    public KpGenTask ownTask(Long id, Long userId) {
        KpGenTask t = taskMapper.selectById(id);
        // 不是你的任务一律当"不存在"，不区分"不存在"和"不是你的"
        if (t == null || !t.getUserId().equals(userId)) {
            throw new CoreCraftException("任务不存在或已删除");
        }
        return t;
    }

    public List<KpGenStep> stepsOf(Long taskId, boolean withResult) {
        List<SFunction<KpGenStep, ?>> cols = new ArrayList<>(List.of(
                KpGenStep::getId, KpGenStep::getTaskId, KpGenStep::getSeq,
                KpGenStep::getPhase, KpGenStep::getPhaseSeq,
                KpGenStep::getOutputSummary, KpGenStep::getNeedConfirm,
                KpGenStep::getTitle, KpGenStep::getTopicId, KpGenStep::getStatus,
                KpGenStep::getContentLen, KpGenStep::getTokenPrompt,
                KpGenStep::getTokenCompletion, KpGenStep::getDurationMs,
                KpGenStep::getFinishReason, KpGenStep::getError));
        if (withResult) {
            cols.add(KpGenStep::getResult);
        }
        return stepMapper.selectList(Wrappers.<KpGenStep>lambdaQuery()
                .select(cols)
                .eq(KpGenStep::getTaskId, taskId)
                .orderByAsc(KpGenStep::getSeq));
    }

    public List<KpGenStep> stepsByPhase(Long taskId, String phase) {
        return stepMapper.selectList(Wrappers.<KpGenStep>lambdaQuery()
                .eq(KpGenStep::getTaskId, taskId)
                .eq(KpGenStep::getPhase, phase)
                .orderByAsc(KpGenStep::getPhaseSeq)
                .orderByAsc(KpGenStep::getSeq));
    }

    public KpGenStep findStep(Long taskId, String phase) {
        List<KpGenStep> list = stepsByPhase(taskId, phase);
        return list.isEmpty() ? null : list.get(0);
    }

    public boolean hasBodySteps(Long taskId) {
        return stepMapper.selectCount(Wrappers.<KpGenStep>lambdaQuery()
                .eq(KpGenStep::getTaskId, taskId)
                .in(KpGenStep::getPhase, List.of(GenTaskNodes.PH_BUILD, GenTaskNodes.PH_WRITE))) > 0;
    }

    public KpGenStep newStep(KpGenTask task, String phase, int phaseSeq, String title) {
        int seq = maxSeq(task.getId()) + 1;
        KpGenStep step = new KpGenStep();
        step.setTaskId(task.getId());
        step.setSeq(seq);
        step.setPhase(phase);
        step.setPhaseSeq(phaseSeq);
        step.setTitle(title);
        step.setStatus(STEP_PENDING);
        step.setNeedConfirm(0);
        stepMapper.insert(step);
        return step;
    }

    public KpGenStep fromReqStep(Long taskId, KpGenTaskCreateReq.Step s, int seq, String taskType) {
        KpGenStep step = new KpGenStep();
        step.setTaskId(taskId);
        step.setSeq(s.getSeq() == null ? seq : s.getSeq());
        step.setPhase(StringUtils.hasText(s.getPhase())
                ? s.getPhase() : GenTaskNodes.legacyPhaseOf(taskType));
        step.setPhaseSeq(s.getPhaseSeq() == null ? seq : s.getPhaseSeq());
        step.setTitle(StringUtils.hasText(s.getTitle()) ? s.getTitle().trim() : "");
        step.setTopicId(s.getTopicId());
        step.setStatus(STEP_PENDING);
        step.setMaterial(s.getMaterial());
        step.setNeedConfirm(0);
        return step;
    }

    /** 插入一条已完成的「建结构」摘要步骤（create 与 continueTask 共享，避免双份构造）。 */
    public void insertBuildSummaryStep(Long taskId, int seq, String buildSummary) {
        KpGenStep build = new KpGenStep();
        build.setTaskId(taskId);
        build.setSeq(seq);
        build.setPhase(GenTaskNodes.PH_BUILD);
        build.setPhaseSeq(1);
        build.setTitle("建结构");
        build.setStatus(STEP_OK);
        build.setOutputSummary(truncate(buildSummary, 190));
        build.setNeedConfirm(0);
        stepMapper.insert(build);
    }

    public int maxSeq(Long taskId) {
        int seq = 0;
        for (KpGenStep s : stepsOf(taskId, false)) {
            seq = Math.max(seq, s.getSeq() == null ? 0 : s.getSeq());
        }
        return seq;
    }

    public int countAll(Long taskId) {
        return stepMapper.selectCount(Wrappers.<KpGenStep>lambdaQuery()
                .eq(KpGenStep::getTaskId, taskId)).intValue();
    }

    public int countOk(Long taskId) {
        return stepMapper.selectCount(Wrappers.<KpGenStep>lambdaQuery()
                .eq(KpGenStep::getTaskId, taskId)
                .eq(KpGenStep::getStatus, STEP_OK)).intValue();
    }

    public int countFailed(Long taskId) {
        return stepMapper.selectCount(Wrappers.<KpGenStep>lambdaQuery()
                .eq(KpGenStep::getTaskId, taskId)
                .eq(KpGenStep::getStatus, STEP_FAILED)).intValue();
    }

    public int resetUnfinishedSteps(List<KpGenStep> steps, List<String> phasesFilter) {
        int reset = 0;
        for (KpGenStep s : steps) {
            if (phasesFilter != null && !phasesFilter.contains(s.getPhase())) {
                continue;
            }
            if (STEP_OK.equals(s.getStatus())) {
                continue;
            }
            stepMapper.update(null, Wrappers.<KpGenStep>lambdaUpdate()
                    .eq(KpGenStep::getId, s.getId())
                    .set(KpGenStep::getStatus, STEP_PENDING)
                    .set(KpGenStep::getError, null));
            reset += 1;
        }
        return reset;
    }

    public void syncProgress(Long taskId) {
        if (taskId == null) {
            return;
        }
        int ok = countOk(taskId);
        int failed = countFailed(taskId);
        writeCounts(taskId, countAll(taskId), ok + failed, failed);
    }

    public void writeCounts(Long taskId, int total, int done, int failed) {
        taskMapper.update(null, Wrappers.<KpGenTask>lambdaUpdate()
                .eq(KpGenTask::getId, taskId)
                .set(KpGenTask::getTotal, total)
                .set(KpGenTask::getDoneCount, done)
                .set(KpGenTask::getFailedCount, failed));
    }

    public void writeStage(Long taskId, String stage) {
        taskMapper.update(null, Wrappers.<KpGenTask>lambdaUpdate()
                .eq(KpGenTask::getId, taskId)
                .set(KpGenTask::getStage, stage));
    }

    public void writePromptFingerprint(Long taskId, int promptLen, String promptMd5) {
        taskMapper.update(null, Wrappers.<KpGenTask>lambdaUpdate()
                .eq(KpGenTask::getId, taskId)
                .set(KpGenTask::getPromptLen, promptLen)
                .set(KpGenTask::getPromptMd5, promptMd5));
    }

    public void mergeParam(KpGenTask task, String key, Object value) {
        ObjectNode node;
        try {
            JsonNode parsed = JsonUtils.readTree(StringUtils.hasText(task.getParams())
                    ? task.getParams() : "{}");
            node = parsed instanceof ObjectNode o ? o : JsonNodeFactory.instance.objectNode();
        } catch (Exception e) {
            node = JsonNodeFactory.instance.objectNode();
        }
        if (value instanceof Long l) {
            node.put(key, l);
        } else if (value instanceof Integer i) {
            node.put(key, i);
        } else if (value instanceof String s) {
            node.put(key, s);
        } else if (value instanceof Boolean b) {
            node.put(key, b);
        } else {
            node.putPOJO(key, value);
        }
        String merged = JsonUtils.toJsonString(node);
        task.setParams(merged);
        taskMapper.update(null, Wrappers.<KpGenTask>lambdaUpdate()
                .eq(KpGenTask::getId, task.getId())
                .set(KpGenTask::getParams, merged));
    }

    public boolean isPaused(String status) {
        return ST_CANCELING.equals(status) || ST_CANCELED.equals(status);
    }

    public String currentStatus(Long taskId) {
        KpGenTask t = taskMapper.selectById(taskId);
        return t == null ? null : t.getStatus();
    }

    public void markPaused(Long taskId) {
        taskMapper.update(null, Wrappers.<KpGenTask>lambdaUpdate()
                .eq(KpGenTask::getId, taskId)
                .set(KpGenTask::getStatus, ST_CANCELED)
                .set(KpGenTask::getFinishTime, AppClock.now()));
    }

    public void finishTask(Long taskId, String status, String error) {
        taskMapper.update(null, Wrappers.<KpGenTask>lambdaUpdate()
                .eq(KpGenTask::getId, taskId)
                .set(KpGenTask::getStatus, status)
                .set(KpGenTask::getError, error)
                .set(KpGenTask::getFinishTime, AppClock.now()));
    }

    /** 列表与详情共用的节点链转换。 */
    public List<GenTaskNodes.StepRow> toStepRows(List<KpGenStep> steps, Map<Long, String> typeOf, String fixedType) {
        List<GenTaskNodes.StepRow> stepRows = new ArrayList<>(steps.size());
        for (KpGenStep s : steps) {
            String type = fixedType != null ? fixedType : typeOf.get(s.getTaskId());
            stepRows.add(new GenTaskNodes.StepRow(s.getTaskId(), type, s.getPhase(),
                    s.getStatus(), s.getDurationMs() == null ? 0L : s.getDurationMs()));
        }
        return stepRows;
    }

    public void attachNodes(List<KpGenTaskResp> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>(list.size());
        Map<Long, String> typeOf = new HashMap<>();
        for (KpGenTaskResp r : list) {
            ids.add(r.getId());
            typeOf.put(r.getId(), r.getType());
        }
        List<KpGenStep> rows = stepMapper.selectList(Wrappers.<KpGenStep>lambdaQuery()
                .select(KpGenStep::getTaskId, KpGenStep::getPhase, KpGenStep::getStatus, KpGenStep::getDurationMs)
                .in(KpGenStep::getTaskId, ids));
        List<GenTaskNodes.StepRow> stepRows = toStepRows(rows, typeOf, null);
        Map<String, Map<String, Map<String, Integer>>> agg = GenTaskNodes.aggregate(stepRows);
        Map<String, Map<String, Long>> ms = GenTaskNodes.durations(stepRows);
        for (KpGenTaskResp r : list) {
            KpGenTask t = new KpGenTask();
            t.setId(r.getId());
            t.setType(r.getType());
            t.setStatus(r.getStatus());
            t.setStage(r.getStage());
            r.setNodeKey(GenTaskNodes.currentNode(t));
            r.setNodes(toRespNodes(t, agg.get(String.valueOf(r.getId())), ms.get(String.valueOf(r.getId()))));
        }
    }

    public List<KpGenTaskResp.Node> toRespNodes(KpGenTask task, Map<String, Map<String, Integer>> agg,
                                                Map<String, Long> msByPhase) {
        List<KpGenTaskResp.Node> out = new ArrayList<>();
        for (GenTaskNodes.Node n : GenTaskNodes.states(task, agg, msByPhase)) {
            KpGenTaskResp.Node d = new KpGenTaskResp.Node();
            d.setKey(n.key());
            d.setName(n.name());
            d.setDesc(n.desc());
            d.setElapsedMs(n.elapsedMs());
            d.setStatus(n.status());
            d.setDone(n.done());
            d.setTotal(n.total());
            d.setSummary(n.summary());
            List<KpGenTaskResp.Group> gs = new ArrayList<>();
            for (GenTaskNodes.Group g : n.groups()) {
                KpGenTaskResp.Group gd = new KpGenTaskResp.Group();
                gd.setKey(g.key());
                gd.setName(g.name());
                gd.setDone(g.done());
                gd.setTotal(g.total());
                gd.setRunning(g.running());
                gd.setFailed(g.failed());
                gs.add(gd);
            }
            d.setGroups(gs);
            out.add(d);
        }
        return out;
    }

    public KpGenTaskResp toResp(KpGenTask t) {
        KpGenTaskResp r = new KpGenTaskResp();
        r.setId(t.getId());
        r.setType(t.getType());
        r.setTitle(t.getTitle());
        r.setStatus(t.getStatus());
        r.setStage(t.getStage());
        r.setTotal(t.getTotal());
        r.setDoneCount(t.getDoneCount());
        r.setFailedCount(t.getFailedCount());
        r.setParams(t.getParams());
        r.setError(t.getError());
        r.setCreateTime(t.getCreateTime());
        r.setStartTime(t.getStartTime());
        r.setFinishTime(t.getFinishTime());
        return r;
    }

    public String paramsField(String params, String key) {
        if (!StringUtils.hasText(params)) {
            return "";
        }
        try {
            JsonNode p = JsonUtils.readTree(params);
            JsonNode v = p.get(key);
            return v == null || v.isNull() ? "" : v.asText("");
        } catch (Exception e) {
            return "";
        }
    }

    public boolean isFatal(Exception e) {
        return e instanceof UpstreamHttpException up && up.isDeterministic();
    }

    public String message(Exception e) {
        if (e instanceof UpstreamHttpException up) {
            return up.toUserMessage();
        }
        String m = e.getMessage();
        return StringUtils.hasText(m) ? m : e.getClass().getSimpleName();
    }

    public String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
