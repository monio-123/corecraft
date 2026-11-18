package com.mo.corecraft.service.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.model.entity.KpGenStep;
import com.mo.corecraft.model.entity.KpGenTask;
import com.mo.corecraft.utils.JsonUtils;
import com.mo.corecraft.utils.OutlineText;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 目录规划任务（{@code plan} / {@code theme} 首段）执行器：骨架 → 逐章展开 → 校验 → 停住等确认。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenTaskPlanExecutor {

    private final GenTaskAiCaller caller;
    private final GenTaskStore store;

    public void runPlan(KpGenTask task) {
        try {
            String theme = planTheme(task);
            String pref = store.paramsField(task.getParams(), "pref");
            Long taskId = task.getId();
            List<OutlineText.Node> tree = new ArrayList<>();
            tree.add(new OutlineText.Node(1, theme, ""));

            // ---- 阶段 1：骨架（已跑成则直接复用 result，暂停恢复不重调模型） ----
            KpGenStep skeletonStep = store.findStep(taskId, GenTaskNodes.PH_SKELETON);
            String skeletonText;
            if (skeletonStep != null && GenTaskStore.STEP_OK.equals(skeletonStep.getStatus())
                    && StringUtils.hasText(skeletonStep.getResult())) {
                skeletonText = skeletonStep.getResult();
                log.info("目录任务 {} 复用已完成的骨架", taskId);
            } else {
                store.writeStage(taskId, GenTaskNodes.PH_SKELETON);
                if (skeletonStep == null) {
                    skeletonStep = store.newStep(task, GenTaskNodes.PH_SKELETON, 1, "生成章节骨架");
                }
                GenTaskAiCaller.CallResult sk = execPlanStep(
                        task, skeletonStep, planSkeletonMaterial(task, theme, pref), GenTaskStore.Q_PLAN_SKELETON);
                skeletonText = sk.text();
                skeletonStep.setResult(skeletonText);
            }

            List<OutlineText.Node> modules = new ArrayList<>();
            for (OutlineText.Node n : OutlineText.parse(skeletonText)) {
                if (n.depth() <= 1) {
                    modules.add(n);
                }
            }
            if (modules.isEmpty()) {
                // 解析失败说明骨架步骤实质失败：不能保留 STEP_OK，否则 resume() 会因无待补步骤直接拒绝恢复。
                String bad = "骨架没解析出任何章节（模型输出不是「1. 章节名 ｜ 定位」的格式）";
                store.steps().update(null, Wrappers.<KpGenStep>lambdaUpdate()
                        .eq(KpGenStep::getId, skeletonStep.getId())
                        .set(KpGenStep::getStatus, GenTaskStore.STEP_FAILED)
                        .set(KpGenStep::getError, bad));
                throw new CoreCraftException(bad);
            }
            skeletonStep.setOutputSummary(modules.size() + " 模块：" + store.truncate(GenTaskPlanner.namesOf(modules), 120));
            store.steps().updateById(skeletonStep);

            // ---- 阶段 2：逐章展开 ----
            store.writeStage(taskId, GenTaskNodes.PH_EXPAND);
            store.writeCounts(taskId, modules.size() + 2, store.countOk(taskId), store.countFailed(taskId));

            List<KpGenStep> expandSteps = ensureExpandSteps(task, modules);
            int okCount = store.countOk(taskId);
            int failCount = store.countFailed(taskId);
            String lastError = null;
            for (int i = 0; i < modules.size(); i++) {
                KpGenStep step = expandSteps.get(i);
                if (GenTaskStore.STEP_OK.equals(step.getStatus()) && StringUtils.hasText(step.getResult())) {
                    GenTaskPlanner.appendFragment(tree, step.getResult());
                    continue;
                }
                if (store.isPaused(store.currentStatus(taskId))) {
                    store.markPaused(taskId);
                    return;
                }
                OutlineText.Node module = modules.get(i);
                try {
                    GenTaskAiCaller.CallResult r = execPlanStep(
                            task, step, GenTaskPlanner.expandMaterial(theme, modules, i, pref), GenTaskStore.Q_PLAN_EXPAND);
                    List<OutlineText.Node> fragment = GenTaskPlanner.appendFragment(tree, r.text());
                    if (fragment.isEmpty()) {
                        throw new CoreCraftException("这一章没有输出可解析的目录");
                    }
                    step.setResult(r.text());
                    step.setOutputSummary(GenTaskPlanner.summaryOf(fragment) + " · " + module.name());
                    store.steps().updateById(step);
                    okCount += 1;
                } catch (Exception e) {
                    failCount += 1;
                    lastError = store.message(e);
                    step.setStatus(GenTaskStore.STEP_FAILED);
                    step.setError(store.truncate(lastError, 480));
                    store.steps().updateById(step);
                    log.warn("目录任务 {} 第 {} 章展开失败：{}", taskId, i + 1, lastError);
                    if (store.isFatal(e)) {
                        break;
                    }
                }
                store.writeCounts(taskId, modules.size() + 2, okCount, failCount);
            }

            // ---- 阶段 3：校验（纯规则，0 token） ----
            store.writeStage(taskId, GenTaskNodes.PH_VERIFY);
            KpGenStep verifyStep = store.findStep(taskId, GenTaskNodes.PH_VERIFY);
            if (verifyStep == null) {
                verifyStep = store.newStep(task, GenTaskNodes.PH_VERIFY, 1, "校验目录");
            }
            List<String> issues = GenTaskPlanner.verifyTree(tree);
            verifyStep.setStatus(GenTaskStore.STEP_OK);
            verifyStep.setOutputSummary(OutlineText.leafCount(tree) + " 点"
                    + (issues.isEmpty() ? " · 无问题" : " · " + issues.size() + " 个问题"));
            store.steps().updateById(verifyStep);
            okCount += 1;

            // ---- 阶段 4：停住等确认 ----
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("outline", OutlineText.render(tree));
            result.put("modules", modules.size());
            result.put("leaves", OutlineText.leafCount(tree));
            result.put("issues", issues);
            result.put("failedChapters", failCount);

            KpGenTask fin = store.tasks().selectById(taskId);
            if (fin == null) {
                return;
            }
            int finRows = store.tasks().update(null, Wrappers.<KpGenTask>lambdaUpdate()
                    .eq(KpGenTask::getId, taskId)
                    .eq(KpGenTask::getStatus, GenTaskStore.ST_RUNNING)
                    .set(KpGenTask::getStage, GenTaskNodes.NODE_CONFIRM)
                    .set(KpGenTask::getStatus, GenTaskStore.ST_AWAITING)
                    .set(KpGenTask::getResult, JsonUtils.toJsonString(result))
                    .set(KpGenTask::getDoneCount, okCount)
                    .set(KpGenTask::getFailedCount, failCount)
                    .set(KpGenTask::getError, failCount > 0
                            ? store.truncate(failCount + " 章没展开：" + lastError, 480) : null));
            if (finRows == 0) {
                store.markPaused(taskId);
            }
        } catch (Exception e) {
            log.error("目录任务 {} 失败：{}", task.getId(), store.message(e));
            store.finishTask(task.getId(), GenTaskStore.ST_FAILED, store.truncate(store.message(e), 480));
        }
    }

    private List<KpGenStep> ensureExpandSteps(KpGenTask task, List<OutlineText.Node> modules) {
        List<KpGenStep> existing = store.stepsByPhase(task.getId(), GenTaskNodes.PH_EXPAND);
        Map<Integer, KpGenStep> bySeq = new HashMap<>();
        for (KpGenStep s : existing) {
            bySeq.put(s.getPhaseSeq() == null ? 0 : s.getPhaseSeq(), s);
        }
        List<KpGenStep> out = new ArrayList<>();
        for (int i = 0; i < modules.size(); i++) {
            KpGenStep s = bySeq.get(i + 1);
            if (s == null) {
                s = store.newStep(task, GenTaskNodes.PH_EXPAND, i + 1, modules.get(i).name());
            } else if (!StringUtils.hasText(s.getTitle())) {
                s.setTitle(modules.get(i).name());
                store.steps().updateById(s);
            }
            out.add(s);
        }
        return out;
    }

    private GenTaskAiCaller.CallResult execPlanStep(KpGenTask task, KpGenStep step,
                                                    String material, String questionKey) throws IOException {
        step.setStatus(GenTaskStore.STEP_RUNNING);
        step.setMaterial(store.truncate(material, 4000));
        store.steps().updateById(step);
        GenTaskAiCaller.CallResult r = caller.callWithLengthRetry(
                task, material, questionKey, "目录任务 " + task.getId() + " 第 " + step.getSeq() + " 步").result();
        step.setDurationMs(r.durationMs());
        step.setPromptSnapshot(r.prompt());
        step.setFinishReason(r.finish());
        step.setTokenPrompt(r.tokenPrompt());
        step.setTokenCompletion(r.tokenCompletion());
        step.setContentLen(r.text().length());
        if (!StringUtils.hasText(r.text())) {
            throw new CoreCraftException("模型没有输出内容");
        }
        step.setStatus(GenTaskStore.STEP_OK);
        store.steps().updateById(step);
        return r;
    }

    private String planTheme(KpGenTask task) {
        String t = store.paramsField(task.getParams(), "theme");
        if (StringUtils.hasText(t)) {
            return t;
        }
        return StringUtils.hasText(task.getTitle()) ? task.getTitle() : "未命名主题";
    }

    private String planSkeletonMaterial(KpGenTask task, String theme, String pref) {
        String m = store.paramsField(task.getParams(), "material");
        if (StringUtils.hasText(m)) {
            return m;
        }
        StringBuilder sb = new StringBuilder("主题：" + theme);
        if (StringUtils.hasText(pref)) {
            sb.append("\n\n生成偏好（按它取舍）：\n").append(pref);
        }
        return sb.toString();
    }
}
