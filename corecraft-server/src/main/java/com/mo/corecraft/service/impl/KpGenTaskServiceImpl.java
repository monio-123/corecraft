package com.mo.corecraft.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.model.entity.KpGenStep;
import com.mo.corecraft.model.entity.KpGenTask;
import com.mo.corecraft.model.req.KpGenTaskContinueReq;
import com.mo.corecraft.model.req.KpGenTaskCreateReq;
import com.mo.corecraft.model.resp.KpGenTaskResp;
import com.mo.corecraft.service.AiEndpointService;
import com.mo.corecraft.service.KpGenTaskService;
import com.mo.corecraft.service.support.GenTaskAiCaller;
import com.mo.corecraft.service.support.GenTaskBuilder;
import com.mo.corecraft.service.support.GenTaskNodes;
import com.mo.corecraft.service.support.GenTaskPlanExecutor;
import com.mo.corecraft.service.support.GenTaskStepRunner;
import com.mo.corecraft.service.support.GenTaskStore;
import com.mo.corecraft.utils.AppClock;
import com.mo.corecraft.utils.JsonUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 后台生成任务服务门面：管理任务生命周期、查询、异步入队与调度分发。
 *
 * <p>具体阶段实现已拆至 {@link GenTaskBuilder}、{@link GenTaskPlanExecutor}、
 * {@link GenTaskStepRunner}、{@link GenTaskAiCaller} 与 {@link GenTaskStore}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KpGenTaskServiceImpl implements KpGenTaskService {

    private static final int WORKERS = 2;
    private static final int QUEUE_SIZE = 20;

    private static final ExecutorService POOL = new ThreadPoolExecutor(
            WORKERS, WORKERS, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(QUEUE_SIZE),
            r -> {
                Thread t = new Thread(r, "gen-task");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.AbortPolicy());

    private final AiEndpointService aiEndpointService;
    private final GenTaskStore store;
    private final GenTaskAiCaller caller;
    private final GenTaskBuilder builder;
    private final GenTaskPlanExecutor planExecutor;
    private final GenTaskStepRunner stepRunner;

    @PreDestroy
    public void shutdownPool() {
        POOL.shutdownNow();
    }

    @PostConstruct
    public void markOrphans() {
        List<KpGenTask> list = store.tasks().selectList(Wrappers.<KpGenTask>lambdaQuery()
                .in(KpGenTask::getStatus, List.of(
                        GenTaskStore.ST_QUEUED, GenTaskStore.ST_RUNNING, GenTaskStore.ST_CANCELING)));
        int paused = 0;
        int interrupted = 0;
        for (KpGenTask t : list) {
            store.steps().update(null, Wrappers.<KpGenStep>lambdaUpdate()
                    .eq(KpGenStep::getTaskId, t.getId())
                    .eq(KpGenStep::getStatus, GenTaskStore.STEP_RUNNING)
                    .set(KpGenStep::getStatus, GenTaskStore.STEP_PENDING));
            if (GenTaskStore.ST_CANCELING.equals(t.getStatus()) || GenTaskStore.ST_QUEUED.equals(t.getStatus())) {
                store.tasks().update(null, Wrappers.<KpGenTask>lambdaUpdate()
                        .eq(KpGenTask::getId, t.getId())
                        .set(KpGenTask::getStatus, GenTaskStore.ST_CANCELED)
                        .set(KpGenTask::getFinishTime, AppClock.now()));
                paused += 1;
            } else {
                store.tasks().update(null, Wrappers.<KpGenTask>lambdaUpdate()
                        .eq(KpGenTask::getId, t.getId())
                        .set(KpGenTask::getStatus, GenTaskStore.ST_FAILED)
                        .set(KpGenTask::getError, "上次跑到一半服务重启了，点「重试」接着跑")
                        .set(KpGenTask::getFinishTime, AppClock.now()));
                interrupted += 1;
            }
        }
        if (!list.isEmpty()) {
            log.info("生成任务：{} 个已暂停、{} 个中断（服务重启后清理）", paused, interrupted);
        }
    }

    @Override
    @Transactional
    public Long create(Long userId, KpGenTaskCreateReq req) {
        String type = StringUtils.hasText(req.getType()) ? req.getType() : GenTaskNodes.TYPE_CONTENT;
        boolean outlineTask = GenTaskNodes.TYPE_PLAN.equals(type) || GenTaskNodes.TYPE_THEME.equals(type);
        if (!outlineTask && (req.getSteps() == null || req.getSteps().isEmpty())) {
            throw new CoreCraftException("任务没有任何步骤");
        }
        KpGenTask task = new KpGenTask();
        task.setUserId(userId);
        task.setType(type);
        task.setTitle(StringUtils.hasText(req.getTitle()) ? req.getTitle().trim() : "");
        task.setStatus(GenTaskStore.ST_QUEUED);
        task.setStage(outlineTask ? GenTaskNodes.PH_SKELETON : GenTaskNodes.legacyPhaseOf(type));
        task.setTotal(req.getSteps() == null ? 0 : req.getSteps().size());
        task.setDoneCount(0);
        task.setFailedCount(0);
        task.setParams(normalizeParams(req.getParams()));
        task.setPromptKey(req.getPromptKey());
        aiEndpointService.requireReady(userId, caller.endpointIdOf(task));

        String buildSummary = store.paramsField(task.getParams(), "buildSummary");
        boolean hasBuildStep = StringUtils.hasText(buildSummary) && !outlineTask;
        if (hasBuildStep) {
            task.setTotal(task.getTotal() + 1);
            task.setDoneCount(1);
        }
        store.tasks().insert(task);

        int seq = 0;
        if (hasBuildStep) {
            store.insertBuildSummaryStep(task.getId(), seq, buildSummary);
        }
        for (KpGenTaskCreateReq.Step s : req.getSteps() == null ? List.<KpGenTaskCreateReq.Step>of() : req.getSteps()) {
            seq += 1;
            store.steps().insert(store.fromReqStep(task.getId(), s, seq, task.getType()));
        }

        Long taskId = task.getId();
        submitAfterCommit(taskId);
        return taskId;
    }

    @Override
    public void cancel(Long id, Long userId) {
        KpGenTask task = store.ownTask(id, userId);
        String st = task.getStatus();
        if (GenTaskStore.ST_QUEUED.equals(st) || GenTaskStore.ST_AWAITING.equals(st)) {
            store.markPaused(id);
            return;
        }
        if (GenTaskStore.ST_RUNNING.equals(st)) {
            store.tasks().update(null, Wrappers.<KpGenTask>lambdaUpdate()
                    .eq(KpGenTask::getId, id)
                    .set(KpGenTask::getStatus, GenTaskStore.ST_CANCELING));
            return;
        }
        if (GenTaskStore.ST_CANCELING.equals(st) || GenTaskStore.ST_CANCELED.equals(st)) {
            return;
        }
        throw new CoreCraftException("这个任务现在停不下来");
    }

    @Override
    public void resume(Long id, Long userId) {
        KpGenTask task = store.ownTask(id, userId);
        if (GenTaskStore.ST_RUNNING.equals(task.getStatus())
                || GenTaskStore.ST_CANCELING.equals(task.getStatus())
                || GenTaskStore.ST_QUEUED.equals(task.getStatus())) {
            throw new CoreCraftException("任务还在跑");
        }
        if (GenTaskStore.ST_AWAITING.equals(task.getStatus())) {
            throw new CoreCraftException("这个任务在等确认，请点「确认」");
        }
        List<KpGenStep> steps = store.stepsOf(id, false);
        int pending = store.resetUnfinishedSteps(steps, null);
        boolean resetSkeleton = steps.stream()
                .anyMatch(s -> !GenTaskStore.STEP_OK.equals(s.getStatus())
                        && GenTaskNodes.PH_SKELETON.equals(s.getPhase()));
        if (pending == 0) {
            throw new CoreCraftException("没有需要补的步骤");
        }
        var taskUpdate = Wrappers.<KpGenTask>lambdaUpdate()
                .eq(KpGenTask::getId, id)
                .set(KpGenTask::getStatus, GenTaskStore.ST_QUEUED)
                .set(KpGenTask::getFailedCount, 0)
                .set(KpGenTask::getDoneCount, Math.max(0, task.getTotal() - pending))
                .set(KpGenTask::getError, null)
                .set(KpGenTask::getFinishTime, null)
                .set(KpGenTask::getStage, "");
        if (resetSkeleton) {
            taskUpdate.set(KpGenTask::getResult, null);
        }
        store.tasks().update(null, taskUpdate);
        submitAfterCommit(id);
    }

    @Override
    public IPage<KpGenTaskResp> page(Page<KpGenTask> page, Long userId, String status, String type) {
        LambdaQueryWrapper<KpGenTask> w = Wrappers.<KpGenTask>lambdaQuery()
                .select(KpGenTask::getId, KpGenTask::getType, KpGenTask::getTitle,
                        KpGenTask::getStatus, KpGenTask::getStage, KpGenTask::getTotal,
                        KpGenTask::getDoneCount, KpGenTask::getFailedCount,
                        KpGenTask::getParams, KpGenTask::getError,
                        KpGenTask::getCreateTime, KpGenTask::getStartTime, KpGenTask::getFinishTime)
                .eq(KpGenTask::getUserId, userId)
                .orderByDesc(KpGenTask::getId);
        if (StringUtils.hasText(status)) {
            w.eq(KpGenTask::getStatus, status);
        }
        if (StringUtils.hasText(type)) {
            w.eq(KpGenTask::getType, type);
        }
        IPage<KpGenTaskResp> out = store.tasks().selectPage(page, w).convert(store::toResp);
        store.attachNodes(out.getRecords());
        return out;
    }

    @Override
    public KpGenTaskResp detail(Long id, Long userId) {
        KpGenTask task = store.ownTask(id, userId);
        KpGenTaskResp resp = store.toResp(task);
        boolean withResult = GenTaskNodes.TYPE_NAME.equals(task.getType())
                || GenTaskNodes.TYPE_QUIZ.equals(task.getType())
                || GenTaskNodes.TYPE_EXAM.equals(task.getType());
        if (GenTaskNodes.TYPE_PLAN.equals(task.getType()) || GenTaskNodes.TYPE_THEME.equals(task.getType())) {
            resp.setResult(task.getResult());
        }
        List<KpGenStep> all = store.stepsOf(id, withResult);
        List<GenTaskNodes.StepRow> stepRows = store.toStepRows(all, null, task.getType());
        resp.setNodeKey(GenTaskNodes.currentNode(task));
        resp.setNodes(store.toRespNodes(task,
                GenTaskNodes.aggregate(stepRows).get(String.valueOf(id)),
                GenTaskNodes.durations(stepRows).get(String.valueOf(id))));

        List<KpGenTaskResp.Step> list = new ArrayList<>(all.size());
        for (KpGenStep s : all) {
            KpGenTaskResp.Step d = new KpGenTaskResp.Step();
            d.setId(s.getId());
            d.setSeq(s.getSeq());
            d.setPhase(StringUtils.hasText(s.getPhase()) ? s.getPhase() : GenTaskNodes.legacyPhaseOf(task.getType()));
            d.setPhaseSeq(s.getPhaseSeq());
            d.setOutputSummary(s.getOutputSummary());
            d.setNeedConfirm(s.getNeedConfirm());
            d.setTitle(s.getTitle());
            d.setTopicId(s.getTopicId());
            d.setStatus(s.getStatus());
            d.setContentLen(s.getContentLen());
            d.setTokenPrompt(s.getTokenPrompt());
            d.setTokenCompletion(s.getTokenCompletion());
            d.setDurationMs(s.getDurationMs());
            d.setFinishReason(s.getFinishReason());
            d.setError(s.getError());
            d.setResult(s.getResult());
            list.add(d);
        }
        resp.setSteps(list);
        return resp;
    }

    @Override
    public void ack(Long id, Long userId) {
        KpGenTask task = store.ownTask(id, userId);
        if (GenTaskStore.ST_AWAITING.equals(task.getStatus())) {
            LocalDateTime now = AppClock.now();
            store.tasks().update(null, Wrappers.<KpGenTask>lambdaUpdate()
                    .eq(KpGenTask::getId, id)
                    .eq(KpGenTask::getStatus, GenTaskStore.ST_AWAITING)
                    .set(KpGenTask::getStatus, GenTaskStore.ST_SUCCEEDED)
                    .set(KpGenTask::getStage, "done")
                    .set(KpGenTask::getConfirmedAt, now)
                    .set(KpGenTask::getFinishTime, now));
            return;
        }
        if (GenTaskStore.ST_SUCCEEDED.equals(task.getStatus()) && task.getConfirmedAt() == null) {
            store.tasks().update(null, Wrappers.<KpGenTask>lambdaUpdate()
                    .eq(KpGenTask::getId, id)
                    .eq(KpGenTask::getStatus, GenTaskStore.ST_SUCCEEDED)
                    .isNull(KpGenTask::getConfirmedAt)
                    .set(KpGenTask::getConfirmedAt, AppClock.now()));
        }
    }

    @Override
    @Transactional
    public void continueTask(Long id, Long userId, KpGenTaskContinueReq req) {
        KpGenTask task = store.ownTask(id, userId);
        List<KpGenTaskCreateReq.Step> steps = req == null || req.getSteps() == null
                ? List.of() : req.getSteps();
        if (steps.isEmpty()) {
            throw new CoreCraftException("没有要继续的步骤");
        }
        if (!GenTaskStore.ST_AWAITING.equals(task.getStatus())) {
            throw new CoreCraftException("这个任务现在不需要确认");
        }
        if (req.getEndpointId() != null) {
            store.mergeParam(task, "endpointId", req.getEndpointId());
        }
        aiEndpointService.requireReady(userId, caller.endpointIdOf(task));

        int seq = store.maxSeq(id);
        boolean asyncBuild = req.getNodes() != null && !req.getNodes().isEmpty();
        if (asyncBuild) {
            builder.enqueueAsyncBuild(id, task.getType(), seq, req, steps);
        } else {
            if (StringUtils.hasText(req.getBuildSummary())) {
                seq += 1;
                store.insertBuildSummaryStep(id, seq, req.getBuildSummary());
            }
            for (KpGenTaskCreateReq.Step s : steps) {
                seq += 1;
                KpGenStep step = store.fromReqStep(id, s, seq, task.getType());
                step.setSeq(seq);
                store.steps().insert(step);
                if (s.getTopicId() == null) {
                    throw new CoreCraftException("有步骤没对上知识点：" + s.getTitle());
                }
            }
        }

        int advanced = store.tasks().update(null, Wrappers.<KpGenTask>lambdaUpdate()
                .eq(KpGenTask::getId, id)
                .eq(KpGenTask::getStatus, GenTaskStore.ST_AWAITING)
                .set(KpGenTask::getStatus, GenTaskStore.ST_QUEUED)
                .set(KpGenTask::getStage, asyncBuild ? GenTaskNodes.NODE_BUILD : GenTaskNodes.PH_WRITE)
                .set(KpGenTask::getFinishTime, null)
                .set(KpGenTask::getError, null)
                .set(KpGenTask::getFailedCount, 0)
                .set(KpGenTask::getConfirmedAt, AppClock.now())
                .set(!StringUtils.hasText(task.getPromptKey()), KpGenTask::getPromptKey, GenTaskStore.Q_POINT)
                .set(KpGenTask::getDoneCount, store.countOk(id))
                .set(KpGenTask::getTotal, store.countAll(id)));
        if (advanced == 0) {
            throw new CoreCraftException("这个任务现在不需要确认");
        }
        submitAfterCommit(id);
    }

    @Override
    @Transactional
    public void retryNode(Long id, Long userId, String nodeKey) {
        KpGenTask task = store.ownTask(id, userId);
        if (GenTaskStore.ST_RUNNING.equals(task.getStatus())
                || GenTaskStore.ST_CANCELING.equals(task.getStatus())
                || GenTaskStore.ST_QUEUED.equals(task.getStatus())) {
            throw new CoreCraftException("任务还在跑");
        }
        List<String> phases = phasesOfNode(task.getType(), nodeKey);
        if (phases.isEmpty()) {
            throw new CoreCraftException("不认识这个节点");
        }
        if (phases.contains("manual")) {
            throw new CoreCraftException("这个节点是人工确认，没有可重跑的内容");
        }
        int reset = store.resetUnfinishedSteps(store.stepsOf(id, false), phases);
        if (reset == 0) {
            throw new CoreCraftException("这个节点没有需要重跑的内容");
        }
        boolean rerunSkeleton = phases.contains(GenTaskNodes.PH_SKELETON);
        int advanced = store.tasks().update(null, Wrappers.<KpGenTask>lambdaUpdate()
                .eq(KpGenTask::getId, id)
                .notIn(KpGenTask::getStatus, GenTaskStore.ST_RUNNING, GenTaskStore.ST_CANCELING)
                .set(rerunSkeleton, KpGenTask::getResult, null)
                .set(KpGenTask::getStatus, GenTaskStore.ST_QUEUED)
                .set(KpGenTask::getError, null)
                .set(KpGenTask::getFinishTime, null)
                .set(KpGenTask::getFailedCount, 0)
                .set(KpGenTask::getDoneCount, store.countOk(id))
                .set(KpGenTask::getTotal, store.countAll(id))
                .set(KpGenTask::getStage, phases.get(0)));
        if (advanced == 0) {
            throw new CoreCraftException("任务还在跑");
        }
        submitAfterCommit(id);
    }

    @Override
    @Transactional
    public void remove(Long id, Long userId) {
        KpGenTask task = store.ownTask(id, userId);
        if (GenTaskStore.ST_RUNNING.equals(task.getStatus())
                || GenTaskStore.ST_CANCELING.equals(task.getStatus())
                || GenTaskStore.ST_QUEUED.equals(task.getStatus())) {
            throw new CoreCraftException("任务还在跑，先暂停再删");
        }
        store.tasks().delete(Wrappers.<KpGenTask>lambdaQuery()
                .eq(KpGenTask::getId, id)
                .eq(KpGenTask::getUserId, userId));
        store.steps().delete(Wrappers.<KpGenStep>lambdaQuery()
                .eq(KpGenStep::getTaskId, id));
    }

    @Override
    @Transactional
    public int removeFinished(Long userId) {
        List<Long> ids = store.tasks().selectList(Wrappers.<KpGenTask>lambdaQuery()
                        .select(KpGenTask::getId)
                        .eq(KpGenTask::getUserId, userId)
                        .eq(KpGenTask::getStatus, GenTaskStore.ST_SUCCEEDED)
                        .and(w -> w.eq(KpGenTask::getFailedCount, 0)
                                .or().isNull(KpGenTask::getFailedCount)))
                .stream()
                .map(KpGenTask::getId)
                .toList();
        if (ids.isEmpty()) {
            return 0;
        }
        store.tasks().delete(Wrappers.<KpGenTask>lambdaQuery()
                .eq(KpGenTask::getUserId, userId)
                .in(KpGenTask::getId, ids));
        store.steps().delete(Wrappers.<KpGenStep>lambdaQuery()
                .in(KpGenStep::getTaskId, ids));
        return ids.size();
    }

    private void submitAfterCommit(Long taskId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit(taskId);
                }
            });
        } else {
            submit(taskId);
        }
    }

    private void submit(Long taskId) {
        try {
            POOL.execute(() -> run(taskId));
        } catch (RejectedExecutionException e) {
            store.finishTask(taskId, GenTaskStore.ST_FAILED, "任务队列已满，稍后再试");
            throw new CoreCraftException("任务队列已满，稍后再试");
        }
    }

    private void run(Long taskId) {
        KpGenTask task = store.tasks().selectById(taskId);
        if (task == null || GenTaskStore.ST_CANCELED.equals(task.getStatus())) {
            return;
        }
        int advanced = store.tasks().update(null, Wrappers.<KpGenTask>lambdaUpdate()
                .eq(KpGenTask::getId, taskId)
                .eq(KpGenTask::getStatus, GenTaskStore.ST_QUEUED)
                .set(KpGenTask::getStatus, GenTaskStore.ST_RUNNING)
                .set(KpGenTask::getStartTime, AppClock.now()));
        if (advanced == 0) {
            log.info("任务 {} 启动时状态已是 {}，不执行", taskId, task.getStatus());
            return;
        }
        task = store.tasks().selectById(taskId);
        if (task == null) {
            return;
        }
        if ((GenTaskNodes.TYPE_PLAN.equals(task.getType()) || GenTaskNodes.TYPE_THEME.equals(task.getType()))
                && !store.hasBodySteps(taskId)) {
            planExecutor.runPlan(task);
            return;
        }
        if (!builder.executeBuildPhase(task)) {
            return;
        }
        stepRunner.executePendingSteps(task);
    }

    private List<String> phasesOfNode(String type, String nodeKey) {
        if (nodeKey == null || nodeKey.isBlank()) {
            return List.of();
        }
        for (GenTaskNodes.NodeDef d : GenTaskNodes.defsOf(type)) {
            if (d.key().equals(nodeKey)) {
                if (d.manual()) {
                    return List.of("manual");
                }
                return d.phases().stream().map(GenTaskNodes.PhaseDef::key).toList();
            }
        }
        return List.of();
    }

    private String normalizeParams(Object params) {
        if (params == null) {
            return null;
        }
        if (params instanceof String s) {
            return s;
        }
        return JsonUtils.toJsonString(params);
    }
}
