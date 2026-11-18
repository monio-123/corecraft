package com.mo.corecraft.service.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.mapper.KpTopicMapper;
import com.mo.corecraft.model.entity.KpGenStep;
import com.mo.corecraft.model.entity.KpGenTask;
import com.mo.corecraft.model.entity.KpTopic;
import com.mo.corecraft.model.req.KpCategoryImportNode;
import com.mo.corecraft.model.req.KpCategoryImportReq;
import com.mo.corecraft.model.req.KpGenTaskContinueReq;
import com.mo.corecraft.model.req.KpGenTaskCreateReq;
import com.mo.corecraft.model.resp.KpCategoryResp;
import com.mo.corecraft.service.KpCategoryService;
import com.mo.corecraft.utils.AppClock;
import com.mo.corecraft.utils.ContentHash;
import com.mo.corecraft.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 目录建树与知识点占位（{@code PH_BUILD} 阶段）的入队与异步执行器。
 */
@Component
@RequiredArgsConstructor
public class GenTaskBuilder {

    private static final int PHASE_SEQ_IMPORT_DIR = 1;
    private static final int PHASE_SEQ_PLACEHOLDER = 2;

    private final KpCategoryService kpCategoryService;
    private final KpTopicMapper topicMapper;
    private final GenTaskStore store;

    /** 把确认后的目录树和待写知识点落为两条 {@code PH_BUILD} 步骤及后续 {@code PH_WRITE} 步骤。 */
    public void enqueueAsyncBuild(Long taskId, String taskType, int baseSeq, KpGenTaskContinueReq req,
                                  List<KpGenTaskCreateReq.Step> steps) {
        ObjectNode treePayload = JsonNodeFactory.instance.objectNode();
        treePayload.put("mountParentId", req.getMountParentId() == null ? 0L : req.getMountParentId());
        treePayload.set("nodes", JsonUtils.readTree(JsonUtils.toJsonString(req.getNodes())));
        String importJson = JsonUtils.toJsonString(treePayload);
        if (importJson.length() > 60000) {
            throw new CoreCraftException("目录结构过大，超出单步存储限制");
        }
        int seq = baseSeq + 1;
        insertPendingBuildStep(taskId, seq, PHASE_SEQ_IMPORT_DIR, "导入目录", importJson);
        seq += 1;
        KpGenStep placeholderStep = insertPendingBuildStep(taskId, seq, PHASE_SEQ_PLACEHOLDER, "建知识点占位", "[]");

        List<Map<String, Object>> topicRefs = new ArrayList<>();
        for (KpGenTaskCreateReq.Step s : steps) {
            seq += 1;
            KpGenStep step = store.fromReqStep(taskId, s, seq, taskType);
            step.setSeq(seq);
            store.steps().insert(step);
            if (step.getTopicId() == null) {
                if (!StringUtils.hasText(s.getTitle())) {
                    throw new CoreCraftException("有步骤没带标题，建不了占位");
                }
                Map<String, Object> ref = new HashMap<>();
                ref.put("stepId", step.getId());
                ref.put("dirPath", s.getDirPath() == null ? "" : s.getDirPath().trim());
                ref.put("title", s.getTitle().trim());
                topicRefs.add(ref);
            }
        }
        String topicsJson = JsonUtils.toJsonString(topicRefs);
        if (topicsJson.length() > 60000) {
            throw new CoreCraftException("知识点清单过大，超出单步存储限制");
        }
        placeholderStep.setMaterial(topicsJson);
        store.steps().updateById(placeholderStep);
    }

    /** 后台异步执行 {@code PH_BUILD} 阶段（导入目录 → 建知识点占位）。返回 false 表示暂停或失败已收尾。 */
    public boolean executeBuildPhase(KpGenTask task) {
        Long taskId = task.getId();
        List<KpGenStep> buildSteps = store.steps().selectList(Wrappers.<KpGenStep>lambdaQuery()
                .eq(KpGenStep::getTaskId, taskId)
                .eq(KpGenStep::getPhase, GenTaskNodes.PH_BUILD)
                .in(KpGenStep::getStatus, List.of(GenTaskStore.STEP_PENDING, GenTaskStore.STEP_FAILED))
                .orderByAsc(KpGenStep::getPhaseSeq, KpGenStep::getSeq));
        if (buildSteps.isEmpty()) {
            return true;
        }
        store.tasks().update(null, Wrappers.<KpGenTask>lambdaUpdate()
                .eq(KpGenTask::getId, taskId)
                .eq(KpGenTask::getStatus, GenTaskStore.ST_RUNNING)
                .set(KpGenTask::getStage, GenTaskNodes.NODE_BUILD));

        Map<String, Long> dirIds = loadExistingBuildDirIds(taskId);
        for (KpGenStep step : buildSteps) {
            KpGenTask cur = store.tasks().selectById(taskId);
            if (cur == null) {
                return false;
            }
            if (store.isPaused(cur.getStatus())) {
                store.markPaused(taskId);
                return false;
            }
            step.setStatus(GenTaskStore.STEP_RUNNING);
            step.setError(null);
            store.steps().updateById(step);
            long t0 = System.currentTimeMillis();
            try {
                boolean isImportDir = Integer.valueOf(PHASE_SEQ_IMPORT_DIR).equals(step.getPhaseSeq())
                        || "导入目录".equals(step.getTitle());
                if (isImportDir) {
                    dirIds = importDirsStep(task.getUserId(), step);
                } else {
                    createPlaceholdersStep(task.getUserId(), step, dirIds);
                }
                step.setStatus(GenTaskStore.STEP_OK);
                step.setDurationMs((int) Math.max(1L, System.currentTimeMillis() - t0));
                store.steps().updateById(step);
                store.syncProgress(taskId);
            } catch (Exception e) {
                String msg = store.message(e);
                step.setStatus(GenTaskStore.STEP_FAILED);
                step.setDurationMs((int) Math.max(1L, System.currentTimeMillis() - t0));
                step.setError(store.truncate(msg, 480));
                store.steps().updateById(step);
                store.syncProgress(taskId);
                store.tasks().update(null, Wrappers.<KpGenTask>lambdaUpdate()
                        .eq(KpGenTask::getId, taskId)
                        .eq(KpGenTask::getStatus, GenTaskStore.ST_RUNNING)
                        .set(KpGenTask::getStatus, GenTaskStore.ST_FAILED)
                        .set(KpGenTask::getFailedCount, store.countFailed(taskId))
                        .set(KpGenTask::getError, store.truncate(msg, 480))
                        .set(KpGenTask::getFinishTime, AppClock.now()));
                return false;
            }
        }
        store.tasks().update(null, Wrappers.<KpGenTask>lambdaUpdate()
                .eq(KpGenTask::getId, taskId)
                .eq(KpGenTask::getStatus, GenTaskStore.ST_RUNNING)
                .set(KpGenTask::getStage, GenTaskNodes.PH_WRITE));
        return true;
    }

    private Map<String, Long> importDirsStep(Long userId, KpGenStep step) {
        JsonNode payload = JsonUtils.readTree(step.getMaterial());
        long mountParentId = payload.path("mountParentId").asLong(0L);
        JsonNode nodesJson = payload.get("nodes");
        List<KpCategoryImportNode> nodes = (nodesJson != null && nodesJson.isArray())
                ? JsonUtils.toListObject(JsonUtils.toJsonString(nodesJson), KpCategoryImportNode.class)
                : List.of();
        KpCategoryImportReq imp = new KpCategoryImportReq();
        imp.setParentId(mountParentId);
        imp.setNodes(nodes);
        List<KpCategoryResp> built = kpCategoryService.importTree(imp, userId);
        Map<String, Long> dirIds = resolveDirPaths(nodes, built, mountParentId);
        step.setResult(JsonUtils.toJsonString(dirIds));
        step.setOutputSummary(store.truncate(built.size() + " 个目录", 190));
        return dirIds;
    }

    private void createPlaceholdersStep(Long userId, KpGenStep step, Map<String, Long> dirIds) {
        JsonNode arr = JsonUtils.readTree(step.getMaterial());
        int created = 0;
        int reused = 0;
        if (arr != null && arr.isArray()) {
            for (JsonNode item : arr) {
                long stepId = item.path("stepId").asLong(0L);
                String dirPath = item.path("dirPath").asText("").trim();
                String title = item.path("title").asText("").trim();
                Long cid = dirIds.get(dirPath);
                if (cid == null) {
                    throw new CoreCraftException("目录里找不到这一条的位置：" + (dirPath.isEmpty() ? title : dirPath));
                }
                KpTopic exist = topicMapper.selectOne(Wrappers.<KpTopic>lambdaQuery()
                        .select(KpTopic::getId)
                        .eq(KpTopic::getUserId, userId)
                        .eq(KpTopic::getCategoryId, cid)
                        .eq(KpTopic::getTitle, title)
                        .last("limit 1"));
                Long topicId;
                if (exist != null) {
                    topicId = exist.getId();
                    reused += 1;
                } else {
                    KpTopic ph = new KpTopic();
                    ph.setUserId(userId);
                    ph.setTitle(title);
                    ph.setContent("");
                    ph.setContentHash(ContentHash.ofTopic(title, ""));
                    ph.setCategoryId(cid);
                    topicMapper.insert(ph);
                    topicId = ph.getId();
                    created += 1;
                }
                if (stepId > 0) {
                    store.steps().update(null, Wrappers.<KpGenStep>lambdaUpdate()
                            .eq(KpGenStep::getId, stepId)
                            .set(KpGenStep::getTopicId, topicId));
                }
            }
        }
        step.setOutputSummary(store.truncate(
                created + " 条" + (reused > 0 ? " · 沿用已有 " + reused + " 条" : ""), 190));
    }

    private Map<String, Long> loadExistingBuildDirIds(Long taskId) {
        Map<String, Long> out = new HashMap<>();
        for (KpGenStep s : store.stepsByPhase(taskId, GenTaskNodes.PH_BUILD)) {
            boolean isImportDir = Integer.valueOf(PHASE_SEQ_IMPORT_DIR).equals(s.getPhaseSeq())
                    || "导入目录".equals(s.getTitle());
            if (GenTaskStore.STEP_OK.equals(s.getStatus()) && isImportDir && StringUtils.hasText(s.getResult())) {
                try {
                    JsonNode obj = JsonUtils.readTree(s.getResult());
                    if (obj != null && obj.isObject()) {
                        obj.properties().forEach(e -> out.put(e.getKey(), e.getValue().asLong()));
                    }
                } catch (Exception ignore) {
                }
            }
        }
        return out;
    }

    private KpGenStep insertPendingBuildStep(Long taskId, int seq, int phaseSeq, String title, String material) {
        KpGenStep step = new KpGenStep();
        step.setTaskId(taskId);
        step.setSeq(seq);
        step.setPhase(GenTaskNodes.PH_BUILD);
        step.setPhaseSeq(phaseSeq);
        step.setTitle(title);
        step.setStatus(GenTaskStore.STEP_PENDING);
        step.setMaterial(material);
        step.setNeedConfirm(0);
        store.steps().insert(step);
        return step;
    }

    private Map<String, Long> resolveDirPaths(List<KpCategoryImportNode> nodes,
                                              List<KpCategoryResp> built, Long mountId) {
        Map<Long, Map<String, Long>> byParent = new HashMap<>();
        for (KpCategoryResp r : built) {
            byParent.computeIfAbsent(r.getParentId(), k -> new HashMap<>())
                    .put(r.getName(), r.getId());
        }
        Map<String, Long> out = new HashMap<>();
        walkDirPaths(nodes, mountId == null ? 0L : mountId, "", byParent, out);
        return out;
    }

    private void walkDirPaths(List<KpCategoryImportNode> nodes, Long parentId, String parentPath,
                              Map<Long, Map<String, Long>> byParent, Map<String, Long> out) {
        for (KpCategoryImportNode n : nodes) {
            String name = n.getName().trim();
            Long id = byParent.getOrDefault(parentId, Map.of()).get(name);
            if (id == null) {
                throw new CoreCraftException("目录建好了却对不上：" + name);
            }
            String path = parentPath.isEmpty() ? name : parentPath + "/" + name;
            out.put(path, id);
            if (n.getChildren() != null && !n.getChildren().isEmpty()) {
                walkDirPaths(n.getChildren(), id, path, byParent, out);
            }
        }
    }
}
