package com.mo.corecraft.service.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.mapper.KpTopicMapper;
import com.mo.corecraft.model.entity.KpGenStep;
import com.mo.corecraft.model.entity.KpGenTask;
import com.mo.corecraft.model.entity.KpTopic;
import com.mo.corecraft.utils.AppClock;
import com.mo.corecraft.utils.ContentHash;
import com.mo.corecraft.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 正文 / 命名 / 出题（{@code write} / {@code name} / {@code exam} / {@code quiz}）步骤循环执行器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenTaskStepRunner {

    private final GenTaskMaterials genTaskMaterials;
    private final KpTopicMapper topicMapper;
    private final GenTaskAiCaller caller;
    private final GenTaskStore store;

    public void executePendingSteps(KpGenTask task) {
        Long taskId = task.getId();
        List<KpGenStep> steps = store.steps().selectList(Wrappers.<KpGenStep>lambdaQuery()
                .eq(KpGenStep::getTaskId, taskId)
                .in(KpGenStep::getPhase, List.of(GenTaskNodes.PH_WRITE, GenTaskNodes.PH_NAME,
                        GenTaskNodes.PH_EXAM, GenTaskNodes.PH_QUIZ))
                .in(KpGenStep::getStatus, List.of(GenTaskStore.STEP_PENDING, GenTaskStore.STEP_FAILED))
                .orderByAsc(KpGenStep::getSeq));

        String lastError = null;
        String fatalError = null;
        for (KpGenStep step : steps) {
            KpGenTask cur = store.tasks().selectById(taskId);
            if (cur == null) {
                return;
            }
            if (store.isPaused(cur.getStatus())) {
                store.markPaused(taskId);
                return;
            }
            if (fatalError != null) {
                skipStep(step, fatalError);
                continue;
            }
            try {
                execStep(task, step);
            } catch (Exception e) {
                lastError = store.message(e);
                step.setStatus(GenTaskStore.STEP_FAILED);
                step.setError(store.truncate(lastError, 480));
                store.steps().updateById(step);
                log.warn("生成任务 {} 第 {} 步失败：{}", taskId, step.getSeq(), lastError);
                if (store.isFatal(e)) {
                    fatalError = lastError;
                    log.error("生成任务 {} 因确定性失败中止：{}", taskId, fatalError);
                }
            }
            store.syncProgress(taskId);
        }

        int okAll = store.countOk(taskId);
        int failedAll = store.countFailed(taskId);
        String finStatus = (okAll == 0 && failedAll > 0) ? GenTaskStore.ST_FAILED : GenTaskStore.ST_SUCCEEDED;
        String finError = null;
        if (fatalError != null) {
            finStatus = GenTaskStore.ST_FAILED;
            finError = store.truncate("已中止：" + fatalError, 480);
        } else if (failedAll > 0) {
            finError = store.truncate(failedAll + " 条失败：" + (lastError == null ? "" : lastError), 480);
        }
        int finRows = store.tasks().update(null, Wrappers.<KpGenTask>lambdaUpdate()
                .eq(KpGenTask::getId, taskId)
                .eq(KpGenTask::getStatus, GenTaskStore.ST_RUNNING)
                .set(KpGenTask::getStatus, finStatus)
                .set(KpGenTask::getTotal, store.countAll(taskId))
                .set(KpGenTask::getDoneCount, okAll + failedAll)
                .set(KpGenTask::getFailedCount, failedAll)
                .set(KpGenTask::getError, finError)
                .set(KpGenTask::getFinishTime, AppClock.now()));
        if (finRows == 0) {
            store.markPaused(taskId);
        }
    }

    private void execStep(KpGenTask task, KpGenStep step) throws IOException {
        step.setStatus(GenTaskStore.STEP_RUNNING);
        store.steps().updateById(step);

        boolean nameStep = GenTaskNodes.PH_NAME.equals(step.getPhase());
        boolean quizStep = GenTaskNodes.PH_QUIZ.equals(step.getPhase()) || GenTaskNodes.PH_EXAM.equals(step.getPhase());
        String material = nameStep
                ? genTaskMaterials.nameMaterial(task, step)
                : quizStep
                ? genTaskMaterials.quizMaterial(task, step)
                : step.getMaterial();
        if (nameStep && !StringUtils.hasText(material)) {
            throw new CoreCraftException("这条内容没有正文，无法命名");
        }
        String questionKey = nameStep
                ? GenTaskStore.NAME_QUESTION_KEY
                : (StringUtils.hasText(task.getPromptKey())
                        ? task.getPromptKey()
                        : (quizStep ? GenTaskStore.Q_EXAM_PAPER : GenTaskStore.Q_POINT));
        GenTaskAiCaller.LengthCall attempted = caller.callWithLengthRetry(
                task, material, questionKey, "任务 " + task.getId() + " 第 " + step.getSeq() + " 条");
        GenTaskAiCaller.CallResult r = attempted.result();
        boolean retried = attempted.retried();

        List<String> leftoverIssues = List.of();
        if (isBodyStep(step) && StringUtils.hasText(r.text())) {
            List<String> issues = GenContentValidator.checkStructure(r.text());
            if (!issues.isEmpty()) {
                log.info("任务 {} 第 {} 条结构检查不过 {}，重写一次", task.getId(), step.getSeq(), issues);
                StringBuilder hint = new StringBuilder(material);
                hint.append("\n\n【上一版没通过检查，请把这一条重写】\n");
                for (String one : issues) {
                    hint.append("- ").append(one).append('\n');
                }
                GenTaskAiCaller.CallResult again = caller.callOnce(
                        task, hint.toString(), GenTaskStore.RETRY_MAX_TOKENS, questionKey);
                if (StringUtils.hasText(again.text())) {
                    r = again;
                    retried = true;
                    leftoverIssues = GenContentValidator.checkStructure(r.text());
                } else {
                    leftoverIssues = issues;
                }
            }
        }

        step.setDurationMs(r.durationMs());
        step.setPromptSnapshot(r.prompt());
        step.setFinishReason(r.finish());
        step.setTokenPrompt(r.tokenPrompt());
        step.setTokenCompletion(r.tokenCompletion());
        step.setContentLen(r.text().length());

        writeStepOutcome(task, step, r, nameStep, quizStep);

        step.setPhase(StringUtils.hasText(step.getPhase()) ? step.getPhase() : GenTaskNodes.PH_WRITE);
        step.setPhaseSeq(step.getPhaseSeq() == null ? step.getSeq() : step.getPhaseSeq());
        step.setOutputSummary(store.truncate(GenContentValidator.contentSummary(r.text(), retried), 190));
        step.setNeedConfirm("length".equals(r.finish()) || !leftoverIssues.isEmpty() ? 1 : 0);
        step.setStatus(GenTaskStore.STEP_OK);
        store.steps().updateById(step);

        if (task.getPromptMd5() == null && StringUtils.hasText(r.prompt())) {
            task.setPromptLen(r.prompt().length());
            task.setPromptMd5(DigestUtils.md5DigestAsHex(r.prompt().getBytes(StandardCharsets.UTF_8)));
            store.writePromptFingerprint(task.getId(), task.getPromptLen(), task.getPromptMd5());
        }
    }

    private void writeStepOutcome(KpGenTask task, KpGenStep step, GenTaskAiCaller.CallResult r,
                                  boolean nameStep, boolean quizStep) {
        if (!StringUtils.hasText(r.text())) {
            return;
        }
        if (isBodyStep(step) && step.getTopicId() != null) {
            String content = GenContentValidator.stripHeading(r.text());
            if (!content.isEmpty()) {
                KpTopic topic = topicMapper.selectById(step.getTopicId());
                if (topic != null && Objects.equals(topic.getUserId(), task.getUserId())) {
                    boolean overwrite = topic.getContent() != null && !topic.getContent().isBlank();
                    String hash = ContentHash.ofTopic(topic.getTitle(), content);
                    store.steps().updateTopicContent(
                            step.getTopicId(), task.getUserId(), content, hash, overwrite ? 1 : 0, AppClock.now());
                }
            }
        } else if (nameStep && step.getTopicId() != null) {
            KpTopic topic = topicMapper.selectById(step.getTopicId());
            if (topic != null && Objects.equals(topic.getUserId(), task.getUserId())
                    && !StringUtils.hasText(topic.getTitle())) {
                String title = normalizeTitle(r.text());
                if (StringUtils.hasText(title)) {
                    String hash = ContentHash.ofTopic(title, topic.getContent());
                    topicMapper.update(null, Wrappers.<KpTopic>lambdaUpdate()
                            .eq(KpTopic::getId, topic.getId())
                            .eq(KpTopic::getUserId, task.getUserId())
                            .set(KpTopic::getTitle, title)
                            .set(KpTopic::getContentHash, hash));
                    step.setResult(JsonUtils.toJsonString(Map.of("title", title)));
                }
            }
        } else if (quizStep) {
            step.setResult(JsonUtils.toJsonString(Map.of("answer", r.text())));
        }
    }

    private void skipStep(KpGenStep step, String reason) {
        step.setStatus(GenTaskStore.STEP_SKIPPED);
        step.setError(store.truncate(reason, 480));
        store.steps().updateById(step);
    }

    private boolean isBodyStep(KpGenStep step) {
        return step != null && GenTaskNodes.PH_WRITE.equals(step.getPhase());
    }

    private static String normalizeTitle(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.strip();
        int nl = t.indexOf('\n');
        if (nl > 0) {
            t = t.substring(0, nl).strip();
        }
        t = t.replaceAll("^[“\"'‘《\\[]+", "").replaceAll("[”\"'’》\\]]+$", "").strip();
        if (t.length() > 100) {
            t = t.substring(0, 100).strip();
        }
        return t;
    }
}
