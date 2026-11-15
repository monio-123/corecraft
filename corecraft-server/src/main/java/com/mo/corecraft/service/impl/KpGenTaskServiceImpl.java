package com.mo.corecraft.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.exception.UpstreamHttpException;
import com.mo.corecraft.mapper.KpGenStepMapper;
import com.mo.corecraft.mapper.KpGenTaskMapper;
import com.mo.corecraft.mapper.KpTopicMapper;
import com.mo.corecraft.model.entity.KpGenStep;
import com.mo.corecraft.model.entity.KpGenTask;
import com.mo.corecraft.model.entity.KpTopic;
import com.mo.corecraft.model.req.AiChatReq;
import com.mo.corecraft.model.req.KpCategoryImportNode;
import com.mo.corecraft.model.req.KpCategoryImportReq;
import com.mo.corecraft.model.query.SysDictItemQuery;
import com.mo.corecraft.model.query.SysDictTypeQuery;
import com.mo.corecraft.model.resp.KpCategoryResp;
import com.mo.corecraft.model.resp.SysDictItemResp;
import com.mo.corecraft.service.KpCategoryService;
import com.mo.corecraft.service.SysDictItemService;
import com.mo.corecraft.service.SysDictTypeService;
import com.mo.corecraft.model.req.KpGenTaskContinueReq;
import com.mo.corecraft.model.req.KpGenTaskCreateReq;
import com.mo.corecraft.model.resp.KpGenTaskResp;
import com.mo.corecraft.service.AiChatService;
import com.mo.corecraft.service.AiEndpointService;
import com.mo.corecraft.service.KpGenTaskService;
import com.mo.corecraft.service.support.GenContentValidator;
import com.mo.corecraft.service.support.GenTaskMaterials;
import com.mo.corecraft.service.support.GenTaskNodes;
import com.mo.corecraft.service.support.GenTaskPlanner;
import com.mo.corecraft.service.support.KpDictCodes;
import com.mo.corecraft.utils.ContentHash;
import com.mo.corecraft.utils.JsonUtils;
import com.mo.corecraft.utils.OutlineText;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 生成任务中心：**把「几百条 AI 调用」从浏览器里搬出来**。
 *
 * <p>执行模型很朴素：一个任务一个线程、任务内**严格串行**（一条写完再写下一条）。
 * 串行不是偷懒 —— 材料里的「已经写过什么」是按顺序攒的，并发会看到不同的中间态，
 * 同一批生成两次结果不一样，而界面上看不出来。
 *
 * <p>⚠️ **单实例假设**：线程池在 JVM 内存里，`@PostConstruct` 会把上次残留的
 * `running` 任务标成中断。将来要是起多个副本，这两处都要换成分布式锁 / 队列。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KpGenTaskServiceImpl implements KpGenTaskService {

    // 任务类型 / 步骤组常量**统一用 {@link GenTaskNodes}**（协议登记处，含各类型的
    /// 节点链与语义说明）—— 本类不再留一份同值私有副本，两份会在同一方法里口径分裂。
    /// 只保留 GenTaskNodes 没有的：
    /** 等人工确认（目录任务专属的阶段值，不进 GenTaskNodes 的步骤组协议） */
    private static final String PH_CONFIRM = "confirm";

    /**
     * 分段规划用的两条提示词。
     *
     * <p>**不复用整棵树的 `ai.prompt.knowledge.plan`**：那条要求"一次输出整棵树"，
     * 拿它去要"只出 6~9 个模块"，模型会把整棵树都吐出来（材料里让它只出一层是压不住的）。
     * 两条各管一段，提示词各自能单独调、单独改。
     */
    private static final String Q_PLAN_SKELETON = "ai.prompt.knowledge.plan.skeleton";
    private static final String Q_PLAN_EXPAND = "ai.prompt.knowledge.plan.expand";

    /**
     * AI 归类用的引导问题（短名 + 附料）。
     *
     * <p>短名不是随便写的：**必须和 sys_config 里那条配置的短名一致**
     * （{@code ai.prompt.question.organize:categories,tags}）——
     * 后端按这个键取提示词、按 {@code contexts} 附上**当下的目录清单**。
     *
     * <p>⚠️ **目录清单由后端现查**（{@code AiChatServiceImpl#appendContext}），
     * 不是前端传上来的：清单必须是**这一次调用那一刻**的目录 ——
     * 前端传的那份是快照，跑的过程中目录可能已被删/改。
     */
    private static final String CLASSIFY_QUESTION_KEY = "organize:categories,tags";
    /** 出题规范（考试 / 随堂测验共用）：问题栏据此说「出题」，提示词取这一条 */
    private static final String Q_EXAM_PAPER = "ai.prompt.exam.paper";

    private static final String ST_QUEUED = "queued";
    private static final String ST_RUNNING = "running";
    private static final String ST_SUCCEEDED = "succeeded";
    private static final String ST_FAILED = "failed";
    /**
     * **正在暂停**：用户点了「暂停」，执行器在**当前这条写完之后**收手。
     *
     * <p>⚠️ **它不是一个"停住了"的终态** —— 是"停的请求已经收到，正在生效"。
     * 收手之后任务会变成 {@link #ST_CANCELED}。
     */
    private static final String ST_CANCELING = "canceling";
    /**
     * **已暂停**（用户口径叫「暂停」，代码里沿用 canceled 这个字面量）。
     *
     * <p>⚠️ **为什么沿用 canceled 不新增 paused**：这一列的值已经落库了
     * （前端筛选、节点状态判定、启动清理都按它写）。改字面量 = 数据迁移 + 三处口径同步，
     * 而"改个名字"不解决任何问题 ⇒ **代码里叫 canceled，界面上叫「暂停」**。
     */
    private static final String ST_CANCELED = "canceled";
    /**
     * **等人确认**：不是失败也不是完成。
     *
     * <p>只在有"人必须看一眼"的产物上出现（目录树）。⚠️ 启动清理**不要**碰它 ——
     * 重启一次就把等确认的任务判死，用户回来会发现树没了、还得重跑四分钟。
     */
    private static final String ST_AWAITING = "awaiting";

    /**
     * **步骤**的状态（不是任务的状态）。
     *
     * <p>⚠️ **和任务的 {@code ST_*} 是两套**：任务的 ok 叫 {@code succeeded}，
     * 步骤的 ok 就叫 {@code ok}（前端 {@code taskTypes.js} 的 stepText 也是这套）。
     * 混用会写出"任务 succeeded、步骤 running"这种看不懂的组合。
     *
     * <p>以前这些字面量在方法体里散着写（{@code "pending"} 出现 3 次、
     * {@code "ok"} 2 次、{@code "failed"} 2 次）⇒ 改一处漏一处，
     * 会出现"步骤卡在 running，重跑也捞不出来"这种查不出来的状态。
     */
    private static final String STEP_PENDING = "pending";
    private static final String STEP_RUNNING = "running";
    private static final String STEP_OK = "ok";
    private static final String STEP_FAILED = "failed";
    /**
     * **没跑**（不是失败）。
     *
     * <p>用于「上游确定性失败 ⇒ 整个任务中止」：剩下的步骤压根没发过请求，
     * 标成 failed 会让"失败 91 条"这种数字虚高，还会让人以为是这 91 条内容有问题。
     * skipped 说的是「因为前面的原因没轮到它」。
     */
    private static final String STEP_SKIPPED = "skipped";

    /** 同时跑几个任务。一条调用 15~200 秒，开太多只是把上游打满，写库不是瓶颈 */
    private static final int WORKERS = 2;
    /** 排队上限。满了直接告诉用户"稍后再试"，**不要静默丢任务** */
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

    // ⚠️ 正文相关的正则/常量（LEAD_HEADING / SECTION / KBFIG_BLOCK 等）已随
    // checkStructure / stripHeading / contentSummary 拆到 `GenContentValidator`（2026-11-15）。

    /**
     * 撞输出上限后重试给到的额度。
     *
     * <p>⚠️ **必须比正常额度大**（正文正常 24576，来自 `ai.policy.point.maxTokens`）：
     * 原来写 16384，一旦正常额度抬上去，重试反而**比正常还小** ——
     * 撞了上限再拿更小的额度去撞，等于这次重试注定白花钱。
     */
    private static final int RETRY_MAX_TOKENS = 32768;

    private final KpGenTaskMapper taskMapper;
    private final KpGenStepMapper stepMapper;
    private final KpTopicMapper topicMapper;
    private final AiChatService aiChatService;
    private final AiEndpointService aiEndpointService;
    // 建目录节点（2026-11-13 起目录在服务端建）：复用导入 / 字典这两套既有能力，
    // 不在任务中心再写一份「建目录规则」（同层重名合并、挂载点归属都在那两处）。
    private final KpCategoryService kpCategoryService;
    private final SysDictTypeService sysDictTypeService;
    private final SysDictItemService sysDictItemService;
    /** 材料拼装（归类 / 出题的后端现查正文）——2026-11-15 从本类拆出，见 GenTaskMaterials */
    private final GenTaskMaterials genTaskMaterials;

    /**
     * 启动清理：进程重启后内存里的线程池没了，库里却还挂着"活着"的状态。
     *
     * <p>⚠️ **两条完全不同的语义，绝不能一锅端**（2026-11-12 用户实测踩到）：
     *
     * <ul>
     *   <li><b>{@code canceling} / {@code queued}</b> —— 前者是**用户自己点的暂停**还没落地，
     *       后者是**还没开始跑**。两者重启后都该转成「已暂停」：一个步骤都没丢，
     *       点「恢复」接着跑就行。<b>绝不能写"服务重启，任务中断"</b>：
     *       用户明明是自己点的暂停，卡片上却挂一条红字「服务重启，任务中断（点重试接着跑）」，
     *       而左下角给的按钮是「恢复」—— 文案和动作自相矛盾，还把锅甩给了服务。</li>
     *   <li><b>{@code running}</b> —— 真的跑到一半被拽下来了。标成中断，让用户点「恢复」补跑。</li>
     * </ul>
     *
     * <p>⚠️ **同时要把卡在 {@code running} 的步骤退回 {@code pending}**：
     * 执行线程已经没了，那条步骤永远不会有人写回结果 ⇒ 节点链上那个节点会**永远呼吸**
     * （用户看到的"明明暂停了，生成正文还在闪"），恢复时也会被当成"在跑"跳过。
     */
    @PostConstruct
    public void markOrphans() {
        List<KpGenTask> list = taskMapper.selectList(Wrappers.<KpGenTask>lambdaQuery()
                .in(KpGenTask::getStatus, List.of(ST_QUEUED, ST_RUNNING, ST_CANCELING)));
        int paused = 0;
        int interrupted = 0;
        for (KpGenTask t : list) {
            // 执行线程已经没了 ⇒ 那条"正在写"的步骤必须重跑（写到一半的正文会被后一次覆盖）
            stepMapper.update(null, Wrappers.<KpGenStep>lambdaUpdate()
                    .eq(KpGenStep::getTaskId, t.getId())
                    .eq(KpGenStep::getStatus, STEP_RUNNING)
                    .set(KpGenStep::getStatus, STEP_PENDING));
            if (ST_CANCELING.equals(t.getStatus()) || ST_QUEUED.equals(t.getStatus())) {
                // 用户的暂停意图（或"还没开始"）⇒ 已暂停，不带任何错误文案
                taskMapper.update(null, Wrappers.<KpGenTask>lambdaUpdate()
                        .eq(KpGenTask::getId, t.getId())
                        .set(KpGenTask::getStatus, ST_CANCELED)
                        .set(KpGenTask::getFinishTime, LocalDateTime.now()));
                paused += 1;
            } else {
                taskMapper.update(null, Wrappers.<KpGenTask>lambdaUpdate()
                        .eq(KpGenTask::getId, t.getId())
                        .set(KpGenTask::getStatus, ST_FAILED)
                        // ⚠️ 文案里的按钮名必须是**卡片上真有的那个**（失败态的主动作是「重试」）
                        .set(KpGenTask::getError, "上次跑到一半服务重启了，点「重试」接着跑")
                        .set(KpGenTask::getFinishTime, LocalDateTime.now()));
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
        // 目录任务的步骤是**跑起来才知道**的（先出几个模块，再一章一章展开）⇒ 允许空 steps
        boolean outlineTask = GenTaskNodes.TYPE_PLAN.equals(type) || GenTaskNodes.TYPE_THEME.equals(type);
        if (!outlineTask && (req.getSteps() == null || req.getSteps().isEmpty())) {
            throw new CoreCraftException("任务没有任何步骤");
        }
        KpGenTask task = new KpGenTask();
        task.setUserId(userId);
        task.setType(type);
        task.setTitle(StringUtils.hasText(req.getTitle()) ? req.getTitle().trim() : "");
        task.setStatus(ST_QUEUED);
        // stage（当前节点标记）按类型回落到自己的第一个步骤组（2026-11-14）：
        // 原来非目录任务一律 write ⇒ 出题任务的「当前节点」标到正文节点上、链上找不到。
        task.setStage(outlineTask ? GenTaskNodes.PH_SKELETON : GenTaskNodes.legacyPhaseOf(type));
        task.setTotal(req.getSteps() == null ? 0 : req.getSteps().size());
        task.setDoneCount(0);
        task.setFailedCount(0);
        // params 两种形状都收（2026-11-15 修随堂 400）：对象 / JSON 字符串——
        // 落库统一归一成 JSON 字符串（下游 buildSummary / readTree 都按字符串读）
        task.setParams(normalizeParams(req.getParams()));
        task.setPromptKey(req.getPromptKey());
        // ⚠️ **建任务时就验端点**（2026-11-16 实跑踩到）：
        // 「还没选好 API」这个错是**每一步都撞同一堵墙** —— 100 条就是 100 次失败，
        // 而任务建成的那一刻就已经注定全失败了（参数快照里没有端点）。
        // ⇒ **在跑之前就拦住**：报错只报一次，用户改好端点重新提交即可。
        // 放在 insert 之前 ⇒ 失败时不留一个"注定了失败"的任务在任务中心里。
        requireEndpointReady(userId, task);

        // **建结构这一步是"已经做完的事"，补记一条完成记录**（2026-11-12）：
        // 目录是前端逐条建出来的，任务从"写正文"开始 ⇒ 若不给它一条记录，
        // 任务卡上就只剩"逐条写"，用户看不出"结构已经建好了"。
        String buildSummary = buildSummary(task.getParams());
        boolean hasBuildStep = StringUtils.hasText(buildSummary) && !outlineTask;
        if (hasBuildStep) {
            task.setTotal(task.getTotal() + 1);
            task.setDoneCount(1);
        }
        taskMapper.insert(task);

        int seq = 0;
        if (hasBuildStep) {
            KpGenStep build = new KpGenStep();
            build.setTaskId(task.getId());
            build.setSeq(seq);
            build.setPhase(GenTaskNodes.PH_BUILD);
            build.setPhaseSeq(1);
            build.setTitle("建结构");
            build.setStatus(STEP_OK);
            build.setOutputSummary(truncate(buildSummary, 190));
            build.setNeedConfirm(0);
            stepMapper.insert(build);
        }
        for (KpGenTaskCreateReq.Step s : req.getSteps() == null ? List.<KpGenTaskCreateReq.Step>of() : req.getSteps()) {
            seq += 1;
            stepMapper.insert(fromReqStep(task.getId(), s, seq, task.getType()));
        }

        // **提交必须在事务提交之后**：执行线程是另一个连接，事务没提交时它读不到任务，
        // 会直接 selectById 得到 null、任务静默不跑（界面上就是"卡在排队"）。
        Long taskId = task.getId();
        submitAfterCommit(taskId);
        return taskId;
    }

    /**
     * **暂停任务**（用户口径「暂停」）。
     *
     * <p>⚠️ **语义是"暂停"不是"终止"**（2026-11-12 用户要求）：
     * 暂停之后**可以恢复**（{@link #resume}），也可以删除；
     * 只有还在跑的任务不能删。
     *
     * <p>三种状态分别处理：
     * <ul>
     *   <li><b>排队中</b> ⇒ 还没开跑 ⇒ 直接置「已暂停」（不用等执行器）。</li>
     *   <li><b>运行中</b> ⇒ 只挂标记 {@code canceling}，执行器在**下一条开始前**收手
     *       （当前那条会写完，不留半截正文）。</li>
     *   <li><b>等确认</b> ⇒ 停着是因为等人确认，人不确认了 ⇒ 直接置「已暂停」。
     *       ⚠️ **产物不入库**：目录树本来就没建进知识库（确认前不落库），
     *       放弃只是"这棵树我不要了"，不会留下半截目录。</li>
     * </ul>
     *
     * <p>⚠️ **只写 status / finish_time 两列**（不整行 updateById）：
     * 整行写会把查出来那一刻的其它列一起写回去（尤其 status 会被执行器的旧对象覆盖）。
     */
    @Override
    public void cancel(Long id, Long userId) {
        KpGenTask task = ownTask(id, userId);
        String st = task.getStatus();
        if (ST_QUEUED.equals(st)) {
            // 还没开跑 ⇒ 直接暂停，不用等执行器
            taskMapper.update(null, Wrappers.<KpGenTask>lambdaUpdate()
                    .eq(KpGenTask::getId, id)
                    .set(KpGenTask::getStatus, ST_CANCELED)
                    .set(KpGenTask::getFinishTime, LocalDateTime.now()));
            return;
        }
        if (ST_RUNNING.equals(st)) {
            // 正在跑 ⇒ 只挂标记，执行器在**下一条开始前**收手（当前这条会写完，不留半截）
            taskMapper.update(null, Wrappers.<KpGenTask>lambdaUpdate()
                    .eq(KpGenTask::getId, id)
                    .set(KpGenTask::getStatus, ST_CANCELING));
            return;
        }
        if (ST_AWAITING.equals(st)) {
            // **等确认的任务也能放弃**（v2：「··· → 放弃」）：
            // 它停着是因为等人确认，人说不确认了 ⇒ 直接标成已暂停。
            taskMapper.update(null, Wrappers.<KpGenTask>lambdaUpdate()
                    .eq(KpGenTask::getId, id)
                    .set(KpGenTask::getStatus, ST_CANCELED)
                    .set(KpGenTask::getFinishTime, LocalDateTime.now()));
            return;
        }
        if (ST_CANCELING.equals(st) || ST_CANCELED.equals(st)) {
            // 已经暂停过了 ⇒ **幂等**，不报错（前端会连点）
            return;
        }
        throw new CoreCraftException("这个任务现在停不下来");
    }

    /**
     * **恢复任务**（把暂停的任务接着跑）。
     *
     * <p>⚠️ **目录 / 主题任务也能恢复了**（2026-11-12 用户要求「暂停后这个按钮就变成恢复」）：
     * 原来这里直接拒绝（"请在节点上重跑"），因为恢复会把骨架重新生成一遍、任务里冒出两套章节。
     * ⇒ 现在**骨架的产物存在步骤的 result 列里**，恢复时直接复用 ⇒ 不重复调模型、不重复章节。
     *
     * <p>⚠️ **等确认的任务不能这样恢复**：它的下一步是"人去建目录"，不是"再调一次模型"。
     * 让它走 resume 会把已完成的步骤重置成待跑，白烧一遍额度。
     */
    @Override
    @Transactional
    public void resume(Long id, Long userId) {
        KpGenTask task = ownTask(id, userId);
        if (ST_RUNNING.equals(task.getStatus()) || ST_CANCELING.equals(task.getStatus())
                || ST_QUEUED.equals(task.getStatus())) {
            throw new CoreCraftException("任务还在跑");
        }
        // **等确认的任务不是"没跑完"**：它的下一步是"人去建目录"，不是"再调一次模型"。
        if (ST_AWAITING.equals(task.getStatus())) {
            throw new CoreCraftException("这个任务在等确认，请点「确认」");
        }
        // resume 只改 status / error，不碰 result ⇒ 不必查 result（省一次大字段传输）
        List<KpGenStep> steps = stepsOf(id, false);
        int pending = 0;
        for (KpGenStep s : steps) {
            if (STEP_OK.equals(s.getStatus())) {
                continue;
            }
            // ⚠️ **目录阶段（骨架 / 展开）失败或没跑完的，重置成待跑**：
            // 它们是"这一次生成"的可重跑单位（骨架有 result 列可复用）。
            //
            // ⚠️ **列级写**（不整行 updateById）：
            // updateById 默认 NOT_NULL ⇒ `setError(null)` 会被**静默跳过**
            // ⇒ 重置后步骤上还挂着上次的错误文案。
            // ⇒ 但 **`result` 绝对不能清**：目录任务的骨架原文就存在这一步的 result 里
            //    （恢复时靠它复用，不重出骨架）。所以只碰 status / error 两列。
            stepMapper.update(null, Wrappers.<KpGenStep>lambdaUpdate()
                    .eq(KpGenStep::getId, s.getId())
                    .set(KpGenStep::getStatus, STEP_PENDING)
                    .set(KpGenStep::getError, null));
            pending += 1;
        }
        if (pending == 0) {
            throw new CoreCraftException("没有需要补的步骤");
        }
        // ⚠️ **列级写**（不整行 updateById）：
        // ① 整行写会把查出来那一刻的其它列一起写回去；
        // ② **updateById 默认 NOT_NULL** ⇒ `setError(null)` / `setFinishTime(null)`
        //    会被**静默跳过** ⇒ 恢复后任务还带着上次的结束时间和旧错误文案，
        //    界面上一个"正在跑"的任务却显示着"上次失败的原因"。
        // ⇒ 恢复 = queued + 清 error + 清 finish_time + 进度重算，四件事一次做完。
        taskMapper.update(null, Wrappers.<KpGenTask>lambdaUpdate()
                .eq(KpGenTask::getId, id)
                .set(KpGenTask::getStatus, ST_QUEUED)
                .set(KpGenTask::getFailedCount, 0)
                .set(KpGenTask::getDoneCount, Math.max(0, task.getTotal() - pending))
                // ⚠️ **清空也要显式 set**：lambdaUpdate 的 set(null) 会真的写 NULL
                .set(KpGenTask::getError, null)
                .set(KpGenTask::getFinishTime, null)
                // ⚠️ **恢复要清掉 stage**：上一次可能停在「展开」的中途，
                // 留着旧 stage 会让界面显示"正在展开第 N 章"而实际在跑骨架。
                // ⚠️ **stage 列 NOT NULL** ⇒ 不能写 null，要写空串。
                .set(KpGenTask::getStage, ""));

        submitAfterCommit(task.getId());
    }

    @Override
    public IPage<KpGenTaskResp> page(Page<KpGenTask> page, Long userId, String status, String type) {
        LambdaQueryWrapper<KpGenTask> w = Wrappers.<KpGenTask>lambdaQuery()
                // ⚠️ **列表不读 `result` 列**（2026-11-12）：那是目录任务的树 JSON，
                // 一棵 232 节点的树约 15KB ⇒ 50 条就是几百 KB 的 JDBC 读回
                // （按「读回字符数 × 48ms/万字符」正好是 3~5s，实测这个接口抖到 4.8s）。
                // 而列表**从头到尾没用过它**：`toResp` 不复制 result，
                // `attachNodes` 里 `t.setResult(r.getResult())` 拿到的永远是 null。
                // ⇒ 只投影卡片真正要画的列（`selectPage` 会按 wrapper 的 select 拼 SQL）。
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
        // ⚠️ **类别筛选在后端做**：前端拿到的是全量列表再筛的话，
        // "筛 AI 归类"和"筛生成主题"看到的条数取决于翻了几页 —— 不可信。
        if (StringUtils.hasText(type)) {
            w.eq(KpGenTask::getType, type);
        }
        IPage<KpGenTaskResp> out = taskMapper.selectPage(page, w).convert(this::toResp);
        attachNodes(out.getRecords());
        return out;
    }

    /**
     * 给一批任务补上**节点链**。
     *
     * <p>⚠️ **步骤聚合要一次查完**（只查 taskId / phase / status 三列）：
     * 逐任务查 = N 次往返；20 个任务就是 20 次（列表接口会慢到用户以为坏了）。
     */
    private void attachNodes(List<KpGenTaskResp> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>();
        // taskId → 类型（老步骤 phase 为空时要按类型回落，见 GenTaskNodes.legacyPhaseOf）
        Map<Long, String> typeOf = new HashMap<>();
        for (KpGenTaskResp r : list) {
            ids.add(r.getId());
            typeOf.put(r.getId(), r.getType());
        }
        List<KpGenStep> rows = stepMapper.selectList(Wrappers.<KpGenStep>lambdaQuery()
                .select(KpGenStep::getTaskId, KpGenStep::getPhase, KpGenStep::getStatus,
                        KpGenStep::getDurationMs)
                .in(KpGenStep::getTaskId, ids));
        List<GenTaskNodes.StepRow> stepRows = new ArrayList<>();
        for (KpGenStep s : rows) {
            stepRows.add(new GenTaskNodes.StepRow(s.getTaskId(),
                    typeOf.get(s.getTaskId()), s.getPhase(), s.getStatus(),
                    s.getDurationMs() == null ? 0L : s.getDurationMs()));
        }
        Map<String, Map<String, Map<String, Integer>>> agg = GenTaskNodes.aggregate(stepRows);
        Map<String, Map<String, Long>> ms = GenTaskNodes.durations(stepRows);
        for (KpGenTaskResp r : list) {
            KpGenTask t = new KpGenTask();
            t.setId(r.getId());
            t.setType(r.getType());
            t.setStatus(r.getStatus());
            t.setStage(r.getStage());
            // ⚠️ **不要在这里补 result**：列表查询是投影的、本来就不带这一列
            // （`toResp` 也不复制它），补上去只会让人误以为列表能用摘要里的树规模。
            r.setNodeKey(GenTaskNodes.currentNode(t));
            r.setNodes(toRespNodes(t, agg.get(String.valueOf(r.getId())),
                    ms.get(String.valueOf(r.getId()))));
        }
    }

    /** 节点链 → 响应对象（msByPhase：每个步骤组花掉的时间，可空） */
    private List<KpGenTaskResp.Node> toRespNodes(KpGenTask task, Map<String, Map<String, Integer>> agg,
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

    @Override
    public KpGenTaskResp detail(Long id, Long userId) {
        KpGenTask task = ownTask(id, userId);
        KpGenTaskResp resp = toResp(task);
        // ⚠️ **只有「产物在步骤里」的任务才查 result**：归类（每条一句建议）和
        // 出题（quiz / exam，整份试卷原文——2026-11-14 起前端消费时解析建卷）。
        // content 任务有几十上百步，它的结果在 kp_topic 里，步骤表那列恒为空 ——
        // 白查一遍只是把 payload 撑大（而 detail 已经在 8~9 秒的边缘了）。
        // ⚠️ **判据是任务类型**（不是步骤 phase）：一个任务里只有一类步骤，
        // 而 `theme` 任务可能既有目录步骤又有正文步骤 —— 查不查 result 是**整批**的取舍。
        boolean withResult = GenTaskNodes.TYPE_CLASSIFY.equals(task.getType())
                || GenTaskNodes.TYPE_QUIZ.equals(task.getType())
                || GenTaskNodes.TYPE_EXAM.equals(task.getType());
        // 目录任务：树在 **task.result**（一次汇总，不挂在步骤上）⇒ 只有详情接口带
        if (GenTaskNodes.TYPE_PLAN.equals(task.getType()) || GenTaskNodes.TYPE_THEME.equals(task.getType())) {
            resp.setResult(task.getResult());
        }
        List<KpGenStep> all = stepsOf(id, withResult);
        // 节点链：按这批步骤聚合（详情接口本来就要查这批步骤 ⇒ 不额外查）。
        // ⚠️ **聚合口径只有一份**：与列表那条路同用 GenTaskNodes.aggregate/durations
        // （老步骤空 phase 按任务类型回落也在里面）——详情再自己写一遍就会出现
        // 「节点链显示归类 45/45、点进去一步都没有」的两边漂移。
        List<GenTaskNodes.StepRow> stepRows = new ArrayList<>();
        for (KpGenStep s : all) {
            stepRows.add(new GenTaskNodes.StepRow(s.getTaskId(), task.getType(), s.getPhase(),
                    s.getStatus(), s.getDurationMs() == null ? 0L : s.getDurationMs()));
        }
        resp.setNodeKey(GenTaskNodes.currentNode(task));
        resp.setNodes(toRespNodes(task,
                GenTaskNodes.aggregate(stepRows).get(String.valueOf(id)),
                GenTaskNodes.durations(stepRows).get(String.valueOf(id))));

        List<KpGenTaskResp.Step> list = new ArrayList<>();
        for (KpGenStep s : all) {
            KpGenTaskResp.Step d = new KpGenTaskResp.Step();
            d.setId(s.getId());
            d.setSeq(s.getSeq());
            // ⚠️ **下发时就把 phase 补上**（老数据是空串）——
            // 前端按 `node.groups` 的 key 分组，空串的步骤会被全部过滤掉（看着像"一步没跑"）。
            // 回落口径与聚合同源（GenTaskNodes.legacyPhaseOf）。
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

    // ==================== 执行 ====================

    @Override
    public void ack(Long id, Long userId) {
        KpGenTask task = ownTask(id, userId);
        if (!ST_AWAITING.equals(task.getStatus())) {
            return;
        }
        // ⚠️ **状态转换用条件更新**（WHERE status=awaiting 才写），不用整行 updateById：
        // 前面那次 check 之后再落地之前，并发的「放弃」（cancel 的 awaiting 分支）可能先写 ——
        // 整行写会把旧快照里的 status 覆盖回去，出现「已暂停的任务被点成了已完成」。
        taskMapper.update(null, Wrappers.<KpGenTask>lambdaUpdate()
                .eq(KpGenTask::getId, id)
                .eq(KpGenTask::getStatus, ST_AWAITING)
                .set(KpGenTask::getStatus, ST_SUCCEEDED)
                .set(KpGenTask::getStage, "done")
                .set(KpGenTask::getFinishTime, LocalDateTime.now()));
    }

    // ==================== v2：单任务化 / 节点级重跑 / 删除 ====================

    /**
     * **同一个任务继续往下跑**（确认之后接着写正文）。
     *
     * <p>这就是 v2 设计的核心：**「生成主题」是一张卡、三个节点**，
     * 不是"目录一张卡 + 正文一张卡"。用户确认完目录 ⇒ 正文那一步挂在**同一个任务**上。
     *
     * <p>⚠️ **正文步骤由前端算好传上来**（{@code buildMaterial} 只有前端那一份，
     * 后端再实现一遍必然漂 ⇒ 漂了的表现是"正文风格和界面上看到的材料对不上"）。
     */
    @Override
    @Transactional
    public void continueTask(Long id, Long userId, KpGenTaskContinueReq req) {
        KpGenTask task = ownTask(id, userId);
        List<KpGenTaskCreateReq.Step> steps = req == null || req.getSteps() == null
                ? List.of() : req.getSteps();
        if (steps.isEmpty()) {
            throw new CoreCraftException("没有要继续的步骤");
        }
        // **只有等确认的任务能这样继续**：它在等人确认，确认完了就往下跑。
        // 已完成的任务再调 ⇒ 那是"重跑"，走 {@link #retryNode}（语义不同、动作不同）。
        if (!ST_AWAITING.equals(task.getStatus())) {
            throw new CoreCraftException("这个任务现在不需要确认");
        }
        // ⚠️ **端点重新验一次**：规划跑完到写正文可能隔了几小时/几天，
        // 用户那时可能已经删了那个端点 ⇒ 续跑时才发现就晚了（跑起来才失败）。
        if (req.getEndpointId() != null) {
            mergeParam(task, "endpointId", req.getEndpointId());
        }
        aiEndpointService.requireReady(userId, endpointIdOf(task));

        // ---- 建目录节点（2026-11-13 起：服务端建，任务链上真实的一段）----
        // 用户确认过的树原样传上来；建目录、建占位都在这里做完，
        // 「确认 → 建目录 → 生成正文」从此一口气跑完 —— 暂停 / 恢复都不会重建
        // （老做法建在前端：确认完没提交就关弹窗，任务还停在待确认，下次点确认又建一遍）。
        int seq = maxSeq(id);
        if (req.getNodes() != null && !req.getNodes().isEmpty()) {
            long t0 = System.currentTimeMillis();
            KpCategoryImportReq imp = new KpCategoryImportReq();
            imp.setNodes(req.getNodes());
            imp.setParentId(req.getMountParentId() == null ? 0L : req.getMountParentId());
            List<KpCategoryResp> built = kpCategoryService.importTree(imp, userId);
            long t1 = System.currentTimeMillis();
            Map<String, Long> dirIds = resolveDirPaths(req.getNodes(), built, imp.getParentId());
            Long typeId = learnTypeId();
            int created = 0;
            int reused = 0;
            for (KpGenTaskCreateReq.Step st : steps) {
                if (st.getTopicId() != null) {
                    continue;
                }
                if (!StringUtils.hasText(st.getTitle())) {
                    throw new CoreCraftException("有步骤没带标题，建不了占位");
                }
                String path = st.getDirPath() == null ? "" : st.getDirPath().trim();
                Long cid = dirIds.get(path);
                if (cid == null) {
                    throw new CoreCraftException("目录里找不到这一条的位置：" + (path.isEmpty() ? st.getTitle() : path));
                }
                // **先查重**：同目录同名沿用已有（重试建目录节点也不会建出一堆重复占位）
                KpTopic exist = topicMapper.selectOne(Wrappers.<KpTopic>lambdaQuery()
                        .select(KpTopic::getId)
                        .eq(KpTopic::getUserId, userId)
                        .eq(KpTopic::getCategoryId, cid)
                        .eq(KpTopic::getTitle, st.getTitle().trim())
                        .last("limit 1"));
                if (exist != null) {
                    st.setTopicId(exist.getId());
                    reused += 1;
                    continue;
                }
                KpTopic ph = new KpTopic();
                ph.setUserId(userId);
                ph.setTitle(st.getTitle().trim());
                // **content 传空串**：占位就是"还没写正文"（null 在 update 里是"不修改"）
                ph.setContent("");
                ph.setCategoryId(cid);
                ph.setTypeId(typeId);
                topicMapper.insert(ph);
                st.setTopicId(ph.getId());
                created += 1;
            }
            long t2 = System.currentTimeMillis();
            seq += 1;
            insertBuildStep(id, seq, "导入目录", built.size() + " 个目录", t1 - t0);
            seq += 1;
            insertBuildStep(id, seq, "建知识点占位",
                    created + " 条" + (reused > 0 ? " · 沿用已有 " + reused + " 条" : ""), t2 - t1);
        } else if (StringUtils.hasText(req.getBuildSummary())) {
            // 老链路（前端已经把目录建好了，服务端只补记一条完成记录）
            seq += 1;
            KpGenStep build = new KpGenStep();
            build.setTaskId(id);
            build.setSeq(seq);
            build.setPhase(GenTaskNodes.PH_BUILD);
            build.setPhaseSeq(1);
            build.setTitle("建结构");
            build.setStatus(STEP_OK);
            build.setOutputSummary(truncate(req.getBuildSummary(), 190));
            build.setNeedConfirm(0);
            stepMapper.insert(build);
        }
        for (KpGenTaskCreateReq.Step s : steps) {
            seq += 1;
            // 续跑的序号由服务端重排（老行为：忽略请求里的 seq，接着已有步骤往下编）
            KpGenStep step = fromReqStep(id, s, seq, task.getType());
            step.setSeq(seq);
            stepMapper.insert(step);
        }

        // **每条步骤都要有落点**：老链路前端建占位后必带 topicId；新链路上面已解析。
        // 谁都没有 ⇒ 跑起来才会报「知识点ID不能为空」，那时候已经烧了一次调用。
        for (KpGenTaskCreateReq.Step st : steps) {
            if (st.getTopicId() == null) {
                throw new CoreCraftException("有步骤没对上知识点：" + st.getTitle());
            }
        }

        // ---- 任务切到「生成正文」节点 ----
        // ⚠️ **条件更新 + 显式 set null**，不用整行 updateById（两个理由）：
        // ① updateById 默认 NOT_NULL 策略会把 setFinishTime(null)/setError(null) **静默跳过**，
        //   残留的旧错误文案 / 旧完成时间会一直挂在重新排队的任务上；
        // ② 不带 WHERE status=awaiting 的话，check 与落地之间并发的「放弃」会被整行覆盖回去。
        int advanced = taskMapper.update(null, Wrappers.<KpGenTask>lambdaUpdate()
                .eq(KpGenTask::getId, id)
                .eq(KpGenTask::getStatus, ST_AWAITING)
                .set(KpGenTask::getStatus, ST_QUEUED)
                .set(KpGenTask::getStage, GenTaskNodes.PH_WRITE)
                .set(KpGenTask::getFinishTime, null)
                .set(KpGenTask::getError, null)
                .set(KpGenTask::getFailedCount, 0)
                // **人已经确认过了**（这一步就是"确认"这个人工节点的产物）⇒ 记一笔：
                // 节点状态靠它显示绿色，不靠"后继有没有步骤"推。
                .set(KpGenTask::getConfirmedAt, LocalDateTime.now())
                .set(KpGenTask::getDoneCount, countOk(id))
                .set(KpGenTask::getTotal, countAll(id)));
        if (advanced == 0) {
            throw new CoreCraftException("这个任务现在不需要确认");
        }

        submitAfterCommit(id);
    }

    /**
     * 路径 → 目录 id。**拼法与前端同一套**（{@code a/b/c}，斜杠分隔）——
     * 材料里的 dirPath 就是这么拼的，两套拼法对不上，步骤就全对不到目录。
     */
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

    /** 知识点类型里 value=learn 那一档的 id（占位都归它） */
    private Long learnTypeId() {
        SysDictTypeQuery tq = new SysDictTypeQuery();
        tq.setCode(KpDictCodes.TOPIC_TYPE);
        var dictType = sysDictTypeService.selectSysDictTypeList(tq).stream().findFirst().orElse(null);
        if (dictType == null) {
            throw new CoreCraftException("知识点类型字典不存在");
        }
        SysDictItemQuery iq = new SysDictItemQuery();
        iq.setDictTypeId(dictType.getId());
        iq.setValue("learn");
        iq.setEnabled(true);
        return sysDictItemService.selectSysDictItemList(iq).stream().findFirst()
                .map(SysDictItemResp::getId)
                .orElseThrow(() -> new CoreCraftException("知识点类型里没有「学习」这一档"));
    }

    /** 建目录节点名下的一条步骤（真实耗时，跑完就 ok） */
    private void insertBuildStep(Long taskId, int seq, String title, String summary, long ms) {
        KpGenStep step = new KpGenStep();
        step.setTaskId(taskId);
        step.setSeq(seq);
        step.setPhase(GenTaskNodes.PH_BUILD);
        step.setPhaseSeq(seq);
        step.setTitle(title);
        step.setStatus(STEP_OK);
        step.setOutputSummary(truncate(summary, 190));
        step.setNeedConfirm(0);
        step.setDurationMs((int) Math.max(0, ms));
        stepMapper.insert(step);
    }

    /**
     * **重跑某个节点**（节点级重试）。
     *
     * <p>判据是**步骤属于哪个 phase**（phase → 节点的映射只有 {@code GenTaskNodes} 那一份）：
     * 目录节点 = skeleton/expand/verify，正文节点 = build/write。
     *
     * <p>⚠️ **重跑目录节点 = 重新出一棵树** ⇒ 之前那棵的产物作废
     * （{@code task.result} 要清掉，否则卡片上显示的还是旧树的规模）。
     */
    @Override
    @Transactional
    public void retryNode(Long id, Long userId, String nodeKey) {
        KpGenTask task = ownTask(id, userId);
        if (ST_RUNNING.equals(task.getStatus()) || ST_CANCELING.equals(task.getStatus())) {
            throw new CoreCraftException("任务还在跑");
        }
        List<String> phases = phasesOfNode(task.getType(), nodeKey);
        if (phases.isEmpty()) {
            throw new CoreCraftException("不认识这个节点");
        }
        // 人工节点（确认 / 采纳）没有可重跑的步骤 ⇒ 直接拒绝，别静默什么都不做。
        if (phases.contains("manual")) {
            throw new CoreCraftException("这个节点是人工确认，没有可重跑的内容");
        }
        List<KpGenStep> steps = stepsOf(id, false);
        int reset = 0;
        for (KpGenStep s : steps) {
            if (!phases.contains(s.getPhase())) {
                continue;
            }
            // **只重置没跑成功的**：已经 ok 的那几步留着
            // （目录节点重跑时，已展开的章节不该再烧一遍额度）。
            if (STEP_OK.equals(s.getStatus())) {
                continue;
            }
            s.setStatus(STEP_PENDING);
            // ⚠️ **列级写 + 显式 set null**：s.setError(null) + updateById 的 NOT_NULL 策略
            // 会把 null 静默跳过 ⇒ 旧错误文案一直挂在已重置成"待跑"的步骤上。
            stepMapper.update(null, Wrappers.<KpGenStep>lambdaUpdate()
                    .eq(KpGenStep::getId, s.getId())
                    .set(KpGenStep::getStatus, STEP_PENDING)
                    .set(KpGenStep::getError, null));
            reset += 1;
        }
        if (reset == 0) {
            throw new CoreCraftException("这个节点没有需要重跑的内容");
        }
        // 目录节点重跑 ⇒ 旧树作废（否则卡片上显示的还是旧树的模块数）
        // ⚠️ **显式 set(result, null)**：原来 task.setResult(null) + updateById 的组合里，
        // NOT_NULL 策略把这次清空**静默跳过了** —— 注释说"旧树作废"，库里从来没作废过。
        boolean rerunSkeleton = phases.contains(GenTaskNodes.PH_SKELETON);
        // 状态/计数全部列级写；只认"不在跑"的任务（并发开跑/再点暂停都不会被覆盖）
        int advanced = taskMapper.update(null, Wrappers.<KpGenTask>lambdaUpdate()
                .eq(KpGenTask::getId, id)
                .notIn(KpGenTask::getStatus, ST_RUNNING, ST_CANCELING)
                .set(rerunSkeleton, KpGenTask::getResult, null)
                .set(KpGenTask::getStatus, ST_QUEUED)
                .set(KpGenTask::getError, null)
                .set(KpGenTask::getFinishTime, null)
                .set(KpGenTask::getFailedCount, 0)
                .set(KpGenTask::getDoneCount, countOk(id))
                .set(KpGenTask::getTotal, countAll(id))
                .set(KpGenTask::getStage, phases.get(0)));
        if (advanced == 0) {
            throw new CoreCraftException("任务还在跑");
        }

        submitAfterCommit(id);
    }

    /**
     * **删除任务**（软删）。
     *
     * <p>⚠️ **只删任务和它的步骤**：已经写进知识库的正文**留在库里** ——
     * "删掉这个任务"≠"删掉生成的知识"。
     *
     * <p>进行中的任务不能删（执行线程还在写步骤，删了它下��把步骤写回来）：
     * ⇒ 先让用户停掉。
     */
    @Override
    @Transactional
    public void remove(Long id, Long userId) {
        KpGenTask task = ownTask(id, userId);
        // ⚠️ **只有"还在跑"的任务不能删**（2026-11-12 用户要求「暂停了就能删除」）：
        // 执行线程还在写步骤，删了它下一步会把步骤写回来。
        // ⇒ 已暂停（canceled）/ 已完成 / 失败 / 待确认 都可以删。
        //   「正在暂停中」（canceling）也拦一下 —— 那个线程还没退出，删了它还会写回来。
        if (ST_RUNNING.equals(task.getStatus()) || ST_QUEUED.equals(task.getStatus())
                || ST_CANCELING.equals(task.getStatus())) {
            throw new CoreCraftException("任务还在跑，请先暂停");
        }
        taskMapper.deleteById(id);
        // 步骤也删（软删）：任务没了，步骤留着没有意义。
        // ⚠️ **不用 updateById**：它默认不写 null ⇒ 删不掉。
        stepMapper.delete(Wrappers.<KpGenStep>lambdaQuery().eq(KpGenStep::getTaskId, id));
    }

    /** 节点 key → 它包含的 phase（**只有 {@code GenTaskNodes} 那一份定义**） */
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

    /** 这个任务最大的 seq（续跑时接在后面） */
    private int maxSeq(Long taskId) {
        int seq = 0;
        for (KpGenStep s : stepsOf(taskId, false)) {
            seq = Math.max(seq, s.getSeq() == null ? 0 : s.getSeq());
        }
        return seq;
    }

    /** 这个任务一共多少步 */
    private int countAll(Long taskId) {
        return stepMapper.selectCount(Wrappers.<KpGenStep>lambdaQuery()
                .eq(KpGenStep::getTaskId, taskId)).intValue();
    }

    /** 这个任务已经成功多少步 */
    private int countOk(Long taskId) {
        return stepMapper.selectCount(Wrappers.<KpGenStep>lambdaQuery()
                .eq(KpGenStep::getTaskId, taskId)
                .eq(KpGenStep::getStatus, STEP_OK)).intValue();
    }

    /** 这个任务失败了多少步 */
    private int countFailed(Long taskId) {
        return stepMapper.selectCount(Wrappers.<KpGenStep>lambdaQuery()
                .eq(KpGenStep::getTaskId, taskId)
                .eq(KpGenStep::getStatus, STEP_FAILED)).intValue();
    }

    /**
     * 把进度（done / failed / total）**按库里全量步骤重算**。
     *
     * <p>⚠️ **不能用本轮循环的增量覆盖**：节点级重跑只重置没成的那几步，
     * 本轮 `done` 从 0 开始数 —— 直接写进去就把之前已成功的全抹了
     * （实测：4 条里 3 条已 ok、重跑 1 条又失败 ⇒ 卡片变成「1/4 失败」，
     * 那 3 条明明还在库里）。
     */
    private void syncProgress(Long taskId) {
        if (taskId == null) {
            return;
        }
        int ok = countOk(taskId);
        int failed = countFailed(taskId);
        writeCounts(taskId, countAll(taskId), ok + failed, failed);
    }

    /**
     * **只写进度计数**（total / done / failed）—— **绝不碰 status**。
     *
     * <p>⚠️⚠️ **这是"暂停失效"的根因所在**（2026-11-12 实测）：
     * 用户 21:12 点了停止（后端把 status 置成 {@code canceling}），
     * 可 21:16 任务还在推进、status 又是 {@code running} ——
     * 因为执行器跑完一步后拿**步骤开始前查出来的旧对象**做整行 {@code updateById}，
     * 旧对象里的 status 还是 {@code running} ⇒ **把 canceling 覆盖回去了**。
     * 于是「下一条开始前查库收手」永远看到 running ⇒ 停止形同虚设。
     *
     * <p>⇒ **凡是"顺带更新进度"的地方，一律只写计数三列**，
     * 状态只能由 {@link #cancel} / {@link #finishTask} / 生命周期节点写。
     *
     * @param taskId 任务 id
     * @param total  总步数
     * @param done   已尝试（成功 + 失败）
     * @param failed 失败数
     */
    private void writeCounts(Long taskId, int total, int done, int failed) {
        taskMapper.update(null, Wrappers.<KpGenTask>lambdaUpdate()
                .eq(KpGenTask::getId, taskId)
                .set(KpGenTask::getTotal, total)
                .set(KpGenTask::getDoneCount, done)
                .set(KpGenTask::getFailedCount, failed));
    }

    /**
     * **只写 stage 一列**（当前跑到哪个阶段）。
     *
     * <p>⚠️ 同样不能整行 {@code updateById}：旧对象里带着过期的 status，
     * 整行写回去就会把「已暂停」覆盖成「进行中」（见 {@link #writeCounts} 的说明）。
     */
    private void writeStage(Long taskId, String stage) {
        taskMapper.update(null, Wrappers.<KpGenTask>lambdaUpdate()
                .eq(KpGenTask::getId, taskId)
                .set(KpGenTask::getStage, stage));
    }

    /**
     * **只写 params 一列**（参数快照合并）。
     *
     * <p>⚠️ 原来这里整行 {@code updateById(task)}：会把旧对象里的 status 一起写回去
     * ⇒ 恰好在用户点暂停的那一瞬间，可能把 status 从 canceling 覆盖回 running。
     */
    private void writeParams(Long taskId, String params) {
        taskMapper.update(null, Wrappers.<KpGenTask>lambdaUpdate()
                .eq(KpGenTask::getId, taskId)
                .set(KpGenTask::getParams, params));
    }

    /**
     * **只写提示词指纹两列**。
     *
     * <p>⚠️ 同上：整行写会把过期的 status 覆盖回去（暂停失效的第二个来源）。
     */
    private void writePromptFingerprint(Long taskId, int promptLen, String promptMd5) {
        taskMapper.update(null, Wrappers.<KpGenTask>lambdaUpdate()
                .eq(KpGenTask::getId, taskId)
                .set(KpGenTask::getPromptLen, promptLen)
                .set(KpGenTask::getPromptMd5, promptMd5));
    }

    /** 参数快照里的端点 id */
    private Long endpointIdOf(KpGenTask task) {
        String raw = paramsField(task, "endpointId");
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 往参数快照里**合并**一个字段（不覆盖别的）。
     *
     * <p>⚠️ **不能整体重写 params**：那会把"主题 / 补充说明 / 偏好"全丢掉
     * （续跑正文时材料里还要用主题）。
     */
    private void mergeParam(KpGenTask task, String key, Object value) {
        // 走 Jackson 而不是手拼字符串：手拼对 String 值不转义，拼出坏 JSON 后
        // 会被 paramsField/applyParams 的 catch 静默吞掉（字段"消失"且无报错）。
        ObjectNode node;
        try {
            JsonNode parsed = JsonUtils.readTree(StringUtils.hasText(task.getParams())
                    ? task.getParams() : "{}");
            node = parsed instanceof ObjectNode o ? o : JsonNodeFactory.instance.objectNode();
        } catch (Exception e) {
            // 旧值不是合法 JSON：放弃旧值（本来也读不出来），从空对象开始合并
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
        // ⚠️ **只写 params 一列**：整行 updateById 会把过期的 status 一起写回去
        // ⇒ 用户刚点暂停，这一行就把 canceling 覆盖回 running（暂停失效的第二个来源）。
        writeParams(task.getId(), merged);
    }

    /**
     * **把任务丢进执行器，但必须等事务提交之后**。
     *
     * <p>执行线程是另一个连接：事务没提交时它 {@code selectById} 查不到任务，
     * 会直接返回、任务**静默不跑**（界面上就是"卡在排队"，点停止也没用）。
     *
     * <p>不在事务里（{@code isSynchronizationActive} 为 false）就直接提交 ——
     * 那时候要么没有事务，要么事务已经是最后一步。
     *
     * <p>⚠️ **建任务和续跑共用这一处**：两处的时序要求完全一样，
     * 各写一份 {@code registerSynchronization} 的话，改一处漏一处 ⇒
     * 症状是"新建的任务偶尔卡在排队"（只在有事务时犯，很难复现）。
     *
     * @param taskId 要执行的任务 id
     */
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
            finishTask(taskId, ST_FAILED, "任务队列已满，稍后再试");
            throw new CoreCraftException("任务队列已满，稍后再试");
        }
    }

    private void run(Long taskId) {
        KpGenTask task = taskMapper.selectById(taskId);
        if (task == null || ST_CANCELED.equals(task.getStatus())) {
            return;
        }
        // ⚠️ **启动也要用条件更新，不能整行写**（2026-11-12「暂停失效」根治）：
        // 从「排队」推进到「运行中」时，若库里已经是 canceling / canceled
        // （用户在这条线程排队期间点了暂停），整行 updateById 会把 status 覆盖回 running
        // ⇒ 任务在被暂停之后又"复活"了。
        // ⇒ **只在状态仍是 queued 时才推进**；推进不了就直接收手。
        int advanced = taskMapper.update(null, Wrappers.<KpGenTask>lambdaUpdate()
                .eq(KpGenTask::getId, taskId)
                .eq(KpGenTask::getStatus, ST_QUEUED)
                .set(KpGenTask::getStatus, ST_RUNNING)
                .set(KpGenTask::getStartTime, LocalDateTime.now()));
        if (advanced == 0) {
            // 不是 queued（已暂停 / 已被别的线程接管）⇒ 不跑。
            log.info("任务 {} 启动时状态已是 {}，不执行", taskId, task.getStatus());
            return;
        }
        // 重新查一次：让后面读到的 status / stage 是最新的
        task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }

        // 目录任务走**分段执行**：骨架 → 逐章展开 → 校验 → 停住等确认。
        // 它不是"一串预先算好的材料"，所以不能走下面那个 for 循环。
        //
        // ⚠️ **判据是「有没有正文步骤」，不是「任务类型」**：
        // `theme` 任务先跑目录（无正文步骤）→ 确认 → 续跑正文（有正文步骤）。
        // 只看类型的话，续跑正文时又会跑一遍目录（4 分钟 + 一堆额度）。
        if ((GenTaskNodes.TYPE_PLAN.equals(task.getType()) || GenTaskNodes.TYPE_THEME.equals(task.getType()))
                && !hasBodySteps(taskId)) {
            runPlan(task);
            return;
        }

        // ⚠️ **只取「这一步该做什么」的步骤**（phase = write / classify）。
        // 不按 phase 过滤的话，`theme` 任务续跑正文时会把**目录阶段失败的章节**
        // 一起捡起来 —— 那几步用的是目录提示词，却在这里跑 ⇒ 模型写出的是目录，
        // 却被当成正文写进知识点（**知识库里出现一整章目录**，且界面上看不出异常）。
        // 目录阶段的失败要重跑，走**节点级重跑**（那是另一条链路，用对的提示词）。
        List<KpGenStep> steps = stepMapper.selectList(Wrappers.<KpGenStep>lambdaQuery()
                .eq(KpGenStep::getTaskId, taskId)
                // exam / quiz（出题，2026-11-14）：单步任务，材料是前端拼好的出题提示词，
                // 产物原文存步骤里——跑法和 write/classify 同一个循环。
                .in(KpGenStep::getPhase, List.of(GenTaskNodes.PH_WRITE, GenTaskNodes.PH_CLASSIFY, GenTaskNodes.PH_EXAM, GenTaskNodes.PH_QUIZ))
                .in(KpGenStep::getStatus, List.of(STEP_PENDING, STEP_FAILED))
                .orderByAsc(KpGenStep::getSeq));

        int done = 0;
        int failed = 0;
        String lastError = null;
        // ⚠️ **确定性失败**：上游 4xx（密钥错/余额不足/模型没开通）。
        // 这种错**再调一次结果一样** ⇒ 不能像普通失败那样"记一条、接着跑下一条"，
        // 那会让 100 条知识**各撞一次同一堵墙**（2026-11-11 实测：上游余额不足，
        // 100 条里 91 条以 402 失败，91 次请求全是白花的钱）。
        // ⇒ 第一条撞上就停整个任务，剩下的步骤标成 skipped（不删，留着可查）。
        String fatalError = null;
        for (KpGenStep step : steps) {
            // 每条开始前看一眼任务状态：用户点了暂停就收手。
            // **正在写的这一条不打断** —— 掐断会留下半截正文，比重写一条更糟。
            KpGenTask cur = taskMapper.selectById(taskId);
            if (cur == null) {
                return;
            }
            if (isPaused(cur.getStatus())) {
                // ⚠️ **收手时只写 status + finish_time 两列**（不整行写）：
                // 整行写会把步骤开始前查到的旧 status 一起带回去。
                markPaused(taskId);
                return;
            }
            if (fatalError != null) {
                // 已经有致命原因了 ⇒ 这一条不跑，直接标 skipped
                skipStep(step, fatalError);
                continue;
            }
            try {
                execStep(task, step);
                done += 1;
            } catch (Exception e) {
                failed += 1;
                lastError = message(e);
                step.setStatus(STEP_FAILED);
                step.setError(truncate(lastError, 480));
                stepMapper.updateById(step);
                log.warn("生成任务 {} 第 {} 步失败：{}", taskId, step.getSeq(), lastError);
                // ⚠️ **判定「确定性失败」**：只有这种才停整个任务。
                // 5xx / 超时 / 网络抖动都是偶发的 —— 那就该继续跑下一条。
                if (isFatal(e)) {
                    fatalError = lastError;
                    log.error("生成任务 {} 因确定性失败中止：{}", taskId, fatalError);
                }
            }
            // **按全量重算**（本轮增量会把"重跑前已经成功的"抹掉）
            // ⚠️ **只写计数，不碰 status**（见 writeCounts 的说明）
            syncProgress(taskId);
        }

        // 一条都没成 ⇒ failed；有成的就算 succeeded，失败明细挂在 task.error 里
        //（部分成功是常态：300 条里挂 3 条，不该让整个任务变红）
        int okAll = countOk(taskId);
        int failedAll = countFailed(taskId);
        // ⚠️ **按全量判**：一条都没成才是 failed。用本轮增量判的话，
        // 节点重跑（只跑失败的几条）永远"一条没成" ⇒ 好好的任务被打回失败。
        String finStatus = (okAll == 0 && failedAll > 0) ? ST_FAILED : ST_SUCCEEDED;
        String finError = null;
        if (fatalError != null) {
            // 致命失败：任务状态一律 failed（哪怕前面有成的），
            // 错误文案说清**为什么停了**和**用户该做什么**。
            finStatus = ST_FAILED;
            finError = truncate("已中止：" + fatalError, 480);
        } else if (failedAll > 0) {
            finError = truncate(failedAll + " 条失败：" + (lastError == null ? "" : lastError), 480);
        }
        // 出题任务（随堂测验 / 章节考试）跑成后**停在「待作答」**（2026-11-14）：
        // 产物只是原文（存在步骤里），点「作答」那一刻前端才解析 + 建卷落库——
        // 与「AI 归类：建议存任务、采纳才写库」同构（解析器 examParse.js 仍只有前端一份）。
        // awaiting 让角标亮、任务卡主操作变「作答」；ack 在作答建卷那一刻调。
        if (ST_SUCCEEDED.equals(finStatus)
                && (GenTaskNodes.TYPE_QUIZ.equals(task.getType())
                    || GenTaskNodes.TYPE_EXAM.equals(task.getType()))) {
            finStatus = ST_AWAITING;
        }
        // ⚠️ **条件更新收尾**（不是"先查再判断再整行写"）：
        // 「查出来 → 判断 → 写回去」这三步之间有个窗口，用户在这中间点了暂停，
        // 写回就会把暂停意图吃掉（status 被改回 succeeded）。
        // ⇒ 把判断塞进 WHERE：**只有库里还是 running 才写终态**；
        // 影响行数为 0 = 用户刚好点了暂停 ⇒ 转成已暂停，不动他的意图。
        int finRows = taskMapper.update(null, Wrappers.<KpGenTask>lambdaUpdate()
                .eq(KpGenTask::getId, taskId)
                .eq(KpGenTask::getStatus, ST_RUNNING)
                .set(KpGenTask::getStatus, finStatus)
                .set(KpGenTask::getTotal, countAll(taskId))
                // 同 `syncProgress`：doneCount = 已尝试（成功 + 失败）
                .set(KpGenTask::getDoneCount, okAll + failedAll)
                .set(KpGenTask::getFailedCount, failedAll)
                .set(KpGenTask::getError, finError)
                .set(KpGenTask::getFinishTime, LocalDateTime.now()));
        if (finRows == 0) {
            // 库里已经不是 running 了 —— 极可能是用户刚点了暂停。
            markPaused(taskId);
            return;
        }
    }

    // ==================== 目录任务：分段执行 ====================

    /**
     * **目录任务：骨架 → 逐章展开 → 校验 → 停住等确认**（2026-11-12）。
     *
     * <p>为什么分段（不是拆得好看）：
     * <ul>
     *   <li>一次铺 232 个节点要 219~268 秒，超过前端 240 秒闸，且**失败就是整棵树没了**；
     *       分段后一次 20~30 秒，坏了只重跑那一章。</li>
     *   <li>展开某一章时上下文只装这一章（主题 + 该章定位 + 兄弟章名），
     *       模型注意力集中，比一次性铺满更不容易"后面几章变薄"。</li>
     *   <li>跑到最后**停下来等人看树**：骨架不合格就重跑，不往知识库里灌垃圾目录。</li>
     * </ul>
     *
     * <p>产物是 {@code task.result} 的一段 JSON（含 outline 文本）——
     * 前端拿到直接当"粘贴的大纲"用，解析规则仍然只有前端那一份。
     */
    private void runPlan(KpGenTask task) {
        try {
            String theme = planTheme(task);
            String pref = planPref(task);
            Long taskId = task.getId();
            List<OutlineText.Node> tree = new ArrayList<>();
            tree.add(new OutlineText.Node(1, theme, ""));

            // ---- 阶段 1：骨架 ----
            // ⚠️ **已跑过的骨架要复用，不重复调模型**（暂停后恢复的前提）：
            // 骨架的原始文本存在**骨架步骤的 result 列**里 ⇒ 恢复时直接读回来解析模块，
            // 不再花一次模型调用（用户原话「暂停了就能恢复」——恢复不是"从头再来"）。
            KpGenStep skeletonStep = findStep(taskId, GenTaskNodes.PH_SKELETON);
            String skeletonText = null;
            if (skeletonStep != null && STEP_OK.equals(skeletonStep.getStatus())
                    && StringUtils.hasText(skeletonStep.getResult())) {
                skeletonText = skeletonStep.getResult();
                log.info("目录任务 {} 复用已完成的骨架", taskId);
            } else {
                writeStage(taskId, GenTaskNodes.PH_SKELETON);
                skeletonStep = newStep(task, GenTaskNodes.PH_SKELETON, 1, "生成章节骨架");
                CallResult sk = execPlanStep(task, skeletonStep, planSkeletonMaterial(task, theme, pref), Q_PLAN_SKELETON);
                skeletonText = sk.text();
                skeletonStep.setResult(skeletonText);
            }

            List<OutlineText.Node> parsed = OutlineText.parse(skeletonText);
            List<OutlineText.Node> modules = new ArrayList<>();
            for (OutlineText.Node n : parsed) {
                // 只要"篇"这一层：模型偶尔会顺手把下面几层也写出来，多出来的一律丢掉
                //（留着会变成重复的章节 —— 展开阶段还会再把它们生成一遍）。
                if (n.depth() <= 1) {
                    modules.add(n);
                }
            }
            if (modules.isEmpty()) {
                throw new CoreCraftException("骨架没解析出任何章节（模型输出不是「1. 章节名 ｜ 定位」的格式）");
            }
            skeletonStep.setOutputSummary(modules.size() + " 模块：" + truncate(GenTaskPlanner.namesOf(modules), 120));
            stepMapper.updateById(skeletonStep);

            // ---- 阶段 2：逐章展开 ----
            writeStage(taskId, GenTaskNodes.PH_EXPAND);
            writeCounts(taskId, modules.size() + 2, countOk(taskId), countFailed(taskId));

            // ⚠️ **先把所有章节的步骤建成 pending**，再逐条跑（2026-11-12 用户要求
            // 「跑完一个就绿一个」）：边跑边建的话，任务卡上永远只看得见已经跑过的那几行，
            // 用户看不到"还有几章没展开"。预建之后 9 行一次性出现，逐行 running → ok。
            // ⇒ **恢复时这些步骤已经存在**（见 ensureExpandSteps）⇒ 不重复建。
            List<KpGenStep> expandSteps = ensureExpandSteps(task, modules);

            int okCount = countOk(taskId);
            int failCount = countFailed(taskId);
            String lastError = null;
            for (int i = 0; i < modules.size(); i++) {
                KpGenStep step = expandSteps.get(i);
                // **已跑成的章节不重跑**：它那段文本已经存下来了（step.result），
                // 直接并回树里 ⇒ 恢复只补没跑成的那些章。
                if (STEP_OK.equals(step.getStatus()) && StringUtils.hasText(step.getResult())) {
                    GenTaskPlanner.appendFragment(tree, step.getResult());
                    continue;
                }
                // 每章开始前看一眼状态：用户点了暂停就收手。
                if (isPaused(currentStatus(taskId))) {
                    markPaused(taskId);
                    return;
                }
                OutlineText.Node module = modules.get(i);
                try {
                    CallResult r = execPlanStep(task, step, GenTaskPlanner.expandMaterial(theme, modules, i, pref), Q_PLAN_EXPAND);
                    List<OutlineText.Node> fragment = GenTaskPlanner.appendFragment(tree, r.text());
                    if (fragment.isEmpty()) {
                        throw new CoreCraftException("这一章没有输出可解析的目录");
                    }
                    step.setResult(r.text());
                    step.setOutputSummary(GenTaskPlanner.summaryOf(fragment) + " · " + module.name());
                    stepMapper.updateById(step);
                    okCount += 1;
                } catch (Exception e) {
                    // **一章坏了不拖垮整棵**：标失败，接着展开下一章；最后在树上留个缺口并说明
                    failCount += 1;
                    lastError = message(e);
                    step.setStatus(STEP_FAILED);
                    step.setError(truncate(lastError, 480));
                    stepMapper.updateById(step);
                    log.warn("目录任务 {} 第 {} 章展开失败：{}", taskId, i + 1, lastError);
                    if (isFatal(e)) {
                        break;
                    }
                }
                // ⚠️ **只写计数，不碰 status**（否则会把用户的暂停覆盖掉）
                writeCounts(taskId, modules.size() + 2, okCount, failCount);
            }

            // ---- 阶段 3：校验（纯规则，0 token） ----
            writeStage(taskId, GenTaskNodes.PH_VERIFY);
            KpGenStep verifyStep = findStep(taskId, GenTaskNodes.PH_VERIFY);
            if (verifyStep == null) {
                verifyStep = newStep(task, GenTaskNodes.PH_VERIFY, 1, "校验目录");
            }
            List<String> issues = GenTaskPlanner.verifyTree(tree);
            verifyStep.setStatus(STEP_OK);
            verifyStep.setOutputSummary(OutlineText.leafCount(tree) + " 点"
                    + (issues.isEmpty() ? " · 无问题" : " · " + issues.size() + " 个问题"));
            stepMapper.updateById(verifyStep);
            okCount += 1;

            // ---- 阶段 4：停住等确认 ----
            String outline = OutlineText.render(tree);
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("outline", outline);
            result.put("modules", modules.size());
            result.put("leaves", OutlineText.leafCount(tree));
            result.put("issues", issues);
            result.put("failedChapters", failCount);

            KpGenTask fin = taskMapper.selectById(taskId);
            if (fin == null) {
                return;
            }
            // ⚠️ **条件更新收尾**（不是"先查再判断再整行写"）：
            // 「查出来 → 判断 → 写回去」这三步之间有个窗口，用户在这中间点了暂停，
            // 写回就会把暂停意图吃掉（status 被改回 awaiting）。
            // ⇒ 把判断塞进 WHERE：**只有库里还是 running 才写终态**；
            // 影响行数为 0 = 用户刚好点了暂停 ⇒ 转成已暂停，不动他的意图。
            int finRows = taskMapper.update(null, Wrappers.<KpGenTask>lambdaUpdate()
                    .eq(KpGenTask::getId, taskId)
                    .eq(KpGenTask::getStatus, ST_RUNNING)
                    .set(KpGenTask::getStage, PH_CONFIRM)
                    .set(KpGenTask::getStatus, ST_AWAITING)
                    .set(KpGenTask::getResult, JsonUtils.toJsonString(result))
                    .set(KpGenTask::getDoneCount, okCount)
                    .set(KpGenTask::getFailedCount, failCount)
                    .set(KpGenTask::getError, failCount > 0
                            ? truncate(failCount + " 章没展开：" + lastError, 480) : null));
            if (finRows == 0) {
                // 库里已经不是 running 了 —— 极可能是用户刚点了暂停。
                markPaused(taskId);
                return;
            }
        } catch (Exception e) {
            log.error("目录任务 {} 失败：{}", task.getId(), message(e));
            finishTask(task.getId(), ST_FAILED, truncate(message(e), 480));
        }
    }

    /**
     * **展开阶段的步骤**（按模块序号一一对应）。
     *
     * <p>⚠️ **已存在就复用，不重建**：恢复时这些步骤还在库里（pending / ok / failed），
     * 重建一遍会出现两套章节（症状：任务卡上章节变多）。
     */
    private List<KpGenStep> ensureExpandSteps(KpGenTask task, List<OutlineText.Node> modules) {
        List<KpGenStep> existing = stepsByPhase(task.getId(), GenTaskNodes.PH_EXPAND);
        Map<Integer, KpGenStep> bySeq = new java.util.HashMap<>();
        for (KpGenStep s : existing) {
            bySeq.put(s.getPhaseSeq() == null ? 0 : s.getPhaseSeq(), s);
        }
        List<KpGenStep> out = new ArrayList<>();
        for (int i = 0; i < modules.size(); i++) {
            KpGenStep s = bySeq.get(i + 1);
            if (s == null) {
                s = newStep(task, GenTaskNodes.PH_EXPAND, i + 1, "展开 · " + modules.get(i).name());
            } else if (!StringUtils.hasText(s.getTitle())) {
                s.setTitle("展开 · " + modules.get(i).name());
                stepMapper.updateById(s);
            }
            out.add(s);
        }
        return out;
    }

    /** 某个阶段的第一步（骨架 / 校验） */
    private KpGenStep findStep(Long taskId, String phase) {
        List<KpGenStep> list = stepsByPhase(taskId, phase);
        return list.isEmpty() ? null : list.get(0);
    }

    /** 某个阶段的全部步骤（按阶段内序号排） */
    private List<KpGenStep> stepsByPhase(Long taskId, String phase) {
        return stepMapper.selectList(Wrappers.<KpGenStep>lambdaQuery()
                .eq(KpGenStep::getTaskId, taskId)
                .eq(KpGenStep::getPhase, phase)
                .orderByAsc(KpGenStep::getPhaseSeq)
                .orderByAsc(KpGenStep::getSeq));
    }


    /** 这个任务有没有正文步骤（build / write） */
    private boolean hasBodySteps(Long taskId) {
        return stepMapper.selectCount(Wrappers.<KpGenStep>lambdaQuery()
                .eq(KpGenStep::getTaskId, taskId)
                .in(KpGenStep::getPhase, List.of(GenTaskNodes.PH_BUILD, GenTaskNodes.PH_WRITE))) > 0;
    }

    /**
     * 这一步是不是「写正文」。
     *
     * <p>⚠️ **判据是 phase，不是任务类型**：正文步骤可能挂在
     * {@code content} 任务上，也可能挂在 {@code theme} 任务上（生成主题的第三段）。
     * 按任务类型判 ⇒ theme 的正文不写库（**正文白生成**，界面上一片绿，用户却没正文）。
     */
    private boolean isBodyStep(KpGenStep step) {
        String phase = step == null ? null : step.getPhase();
        return GenTaskNodes.PH_WRITE.equals(phase) || GenTaskNodes.PH_BUILD.equals(phase);
    }

    /** 新建一条 pending 步骤（阶段内的序号自己给） */
    /**
     * 把前端传来的步骤描述落成一行 pending 步骤（**create 与 continueTask 共用这一段**
     * ——两处各写一份的话，phase 回落 / trim 这些口径改一处漏一处）。
     *
     * <p>⚠️ **没传 phase 时按任务类型回落**（不是一律 write）：
     * 归类任务不传 phase ⇒ 步骤被写成 write ⇒ 执行时走"写正文"分支、
     * 材料为空 ⇒ 报一句「知识点ID不能为空!」（实测：归类任务 4 条全挂在这）。
     * 口径复用 {@link GenTaskNodes#legacyPhaseOf}（和老数据补 phase 的那条规则同一份）。
     */
    private KpGenStep fromReqStep(Long taskId, KpGenTaskCreateReq.Step s, int seq, String taskType) {
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

    /** 新建一条 pending 步骤（阶段内的序号自己给） */
    private KpGenStep newStep(KpGenTask task, String phase, int phaseSeq, String title) {
        List<KpGenStep> all = stepsOf(task.getId(), false);
        int seq = 1;
        for (KpGenStep s : all) {
            seq = Math.max(seq, s.getSeq() == null ? 0 : s.getSeq() + 1);
        }
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

    /** 目录任务的一步：调一次 + 记计量。**不做结构重写**（规划的结构由校验阶段统一判） */
    private CallResult execPlanStep(KpGenTask task, KpGenStep step, String material, String questionKey) throws IOException {
        step.setStatus(STEP_RUNNING);
        step.setMaterial(truncate(material, 4000));
        stepMapper.updateById(step);
        CallResult r = callOnce(task, material, null, questionKey);
        // 撞输出上限 ⇒ 涨额度重试一次（和正文同一条规则：同额度重试=再撞一次）
        if ("length".equals(r.finish())) {
            log.info("目录任务 {} 第 {} 步撞输出上限，涨额度重试", task.getId(), step.getSeq());
            CallResult again = callOnce(task, material, RETRY_MAX_TOKENS, questionKey);
            if (StringUtils.hasText(again.text())) {
                r = again;
            }
        }
        step.setDurationMs(r.durationMs());
        step.setPromptSnapshot(r.prompt());
        step.setFinishReason(r.finish());
        step.setTokenPrompt(r.tokenPrompt());
        step.setTokenCompletion(r.tokenCompletion());
        step.setContentLen(r.text().length());
        if (!StringUtils.hasText(r.text())) {
            throw new CoreCraftException("模型没有输出内容");
        }
        step.setStatus(STEP_OK);
        stepMapper.updateById(step);
        return r;
    }

    private String planTheme(KpGenTask task) {
        String t = paramsField(task, "theme");
        if (StringUtils.hasText(t)) {
            return t;
        }
        return StringUtils.hasText(task.getTitle()) ? task.getTitle() : "未命名主题";
    }

    private String planPref(KpGenTask task) {
        return paramsField(task, "pref");
    }

    /** 骨架的材料：前端算好的那段（主题 + 补充说明 + 现有目录清单），没给就现拼一句 */
    private String planSkeletonMaterial(KpGenTask task, String theme, String pref) {
        String m = paramsField(task, "material");
        if (StringUtils.hasText(m)) {
            return m;
        }
        StringBuilder sb = new StringBuilder("主题：" + theme);
        if (StringUtils.hasText(pref)) {
            sb.append("\n\n生成偏好（按它取舍）：\n").append(pref);
        }
        return sb.toString();
    }

    // 规划的纯算法段（expandMaterial / namesOf / summaryOf / appendFragment / verifyTree）
    // 已拆到 `service/support/GenTaskPlanner.java`（2026-11-15）——纯函数，判据只有那一份。

    private String paramsField(KpGenTask task, String key) {
        if (!StringUtils.hasText(task.getParams())) {
            return "";
        }
        try {
            JsonNode p = JsonUtils.readTree(task.getParams());
            JsonNode v = p.get(key);
            return v == null || v.isNull() ? "" : v.asText("");
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 这个失败是不是「确定性」的（再调一次也一样）⇒ 该中止整个任务。
     *
     * <p>判据只有一条：**上游返回了 4xx（429 除外）**。
     * 密钥错、余额不足、模型没开通 —— 这几样都不是重试能解决的，
     * 而批量任务里有 100 条知识，每条都去撞一次 = 100 次白花钱的请求。
     *
     * <p>反过来，5xx / 超时 / 连接重置都是偶发的，**不该停** ——
     * 停了就等于「一条网络抖动毁掉整个批次」。
     */
    private boolean isFatal(Exception e) {
        return e instanceof UpstreamHttpException up && up.isDeterministic();
    }

    /** 把还没跑的步骤标成 skipped，并写明为什么没跑 */
    private void skipStep(KpGenStep step, String reason) {
        step.setStatus(STEP_SKIPPED);
        step.setError(truncate(reason, 480));
        stepMapper.updateById(step);
    }

    /**
     * 执行一步：**调用一次（必要时重试）+ 落库**。
     *
     * <p>重试规则只有两条，都能一眼看出对错：
     * ① 撞输出上限（`finish_reason=length`）⇒ **涨额度重试一次**（同额度重试＝把同一堵墙再撞一遍）；
     * ② 结构下限不过（段落 / 围栏 / kbfig 的 JSON / 长度 / 占位话）⇒ 带问题清单重写一次。
     * 两条都**只重试一次**：连续两次不行基本是配置或内容本身的问题，再问白烧额度。
     *
     * <p>**提示词快照就是 `delta.prompt()`** —— 后端本来就把它原样发给前端过一次，
     * 这里存同一份，不重拼（重拼一次就多一次偏差的机会）。
     */
    private void execStep(KpGenTask task, KpGenStep step) throws IOException {
        step.setStatus(STEP_RUNNING);
        stepMapper.updateById(step);

        // ⚠️ **归类的材料由后端现查**（写正文的仍用前端拼好的那段）：
        // 前端那边的结构索引**刻意不含正文**（几十万字符，列表页读全文实测 4.4 秒），
        // 而"待归类"那一屏拿到的就是结构索引 ⇒ 前端**根本没有正文**，
        // 让它传材料只能传个空壳（只有标题）⇒ 模型判不了该归哪。
        // ⇒ 和"目录清单由后端现查"同一个道理：**这次调用要用到的知识，后端自己捞**。
        //
        // ⚠️ **判据是「这一步的 phase」，不是任务类型**：
        // 归类步骤的 phase 是 `classify`，正文是 `write`。
        // 按任务类型判的话，将来 `theme` 任务里挂一个归类步骤就会用错提示词。
        boolean classifyStep = GenTaskNodes.PH_CLASSIFY.equals(step.getPhase());
        // ⚠️ **出题/考试步骤的材料 = 出题指令 + 后端现查的知识正文**（2026-11-15 修
        // 随堂「出不了题」）：前端传的是**指令**（题量结构+范围+规范），正文得后端捞——
        // 「给 material 就不捞知识」那条规则是为判卷设的（material 是整卷，别再捞一遍
        // 几十 KB 正文）⇒ 任务路径里正文压根没进 prompt，模型只能答「出不了题」。
        // 和归类步骤「材料由后端现查」同一个道理：这次调用要用到的知识，后端自己捞。
        boolean quizStep = GenTaskNodes.PH_QUIZ.equals(step.getPhase()) || GenTaskNodes.PH_EXAM.equals(step.getPhase());
        String material = classifyStep
                ? genTaskMaterials.classifyMaterial(task, step)
                : quizStep
                ? genTaskMaterials.quizMaterial(task, step)
                : step.getMaterial();
        // ⚠️ **空材料要当场说清楚原因**：
        // 归类步骤的材料是后端现查的，查不到（知识点已删 / 不是本人的 / 正文为空）
        // 就是空串。空材料丢进 `AiChatService` 会走到"既没材料又没 topicId"那条路，
        // 报出来的是「知识点ID不能为空!」—— 用户看不懂、也不知道该干嘛（实测踩到）。
        if (classifyStep && !StringUtils.hasText(material)) {
            throw new CoreCraftException("这条知识没有可用的标题和正文（可能已删除），无法归类");
        }
        // ⚠️ **questionKey 按这一步要做什么给**：写正文用 `task.getPromptKey()`（那条配置），
        // 归类用固定的 `organize:categories,tags` —— 归类的提示词是**一条固定的配置**
        // （「补全这条知识：标题 / 目录 / 标签」），不该由前端传什么就用什么。
        String questionKey = classifyStep
                ? CLASSIFY_QUESTION_KEY
                : task.getPromptKey();
        CallResult r = callOnce(task, material, null, questionKey);
        boolean retried = false;

        // ① 撞输出上限 ⇒ 涨额度重试（老链路在前端有这一手，搬到服务端必须补上，
        //    否则长条目会被截断后原样落库，界面上看不出是截断的）
        if ("length".equals(r.finish())) {
            log.info("任务 {} 第 {} 条撞输出上限，涨额度重试", task.getId(), step.getSeq());
            CallResult again = callOnce(task, material, RETRY_MAX_TOKENS, questionKey);
            if (StringUtils.hasText(again.text())) {
                r = again;
                retried = true;
            }
        }

        // ② 结构下限不过 ⇒ 带问题重写一次（替代老链路前端那次校验重试）
        //    **重试仍不合格也照常落库**：宁可留一条格式瑕疵的正文，也不要留空。
        //    ⚠️ **判据是「这一步在写正文」，不是「任务类型」**：
        //    `theme` 任务（生成主题）的正文步骤挂在同一个任务上，
        //    按任务类型判会**漏掉结构重写**（正文质量下降，且界面上看不出来）。
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
                CallResult again = callOnce(task, hint.toString(), RETRY_MAX_TOKENS, questionKey);
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

        if (isBodyStep(step) && step.getTopicId() != null && StringUtils.hasText(r.text())) {
            String content = GenContentValidator.stripHeading(r.text());
            if (!content.isEmpty()) {
                // 内容版本锚点跟着维护（实施计划 §1.3）：覆盖非空正文 = 重新生成 ⇒ epoch 递增；
                // 空占位首次生成不构成版本问题（设计 §9.2）。hash 只用内容本身，不碰 NOW()/时钟坑。
                KpTopic topic = topicMapper.selectById(step.getTopicId());
                if (topic != null && Objects.equals(topic.getUserId(), task.getUserId())) {
                    boolean overwrite = topic.getContent() != null && !topic.getContent().isBlank();
                    String hash = ContentHash.ofTopic(topic.getTitle(), content);
                    stepMapper.updateTopicContent(step.getTopicId(), task.getUserId(), content, hash, overwrite ? 1 : 0);
                }
            }
        } else if ((classifyStep || GenTaskNodes.PH_EXAM.equals(step.getPhase()) || GenTaskNodes.PH_QUIZ.equals(step.getPhase()))
                && StringUtils.hasText(r.text())) {
            // **AI 归类 / 出题：产物只存，不写库**。
            // ⚠️ 存的是**原始回答**，不是解析后的结构 —— 解析规则在前端只有一份
            // （归类的建议要按当下目录清单校验；出题的试卷解析器是 examParse.js，
            // 考试页和随堂测验共用，搬进后端就是两份必然漂）。
            // ⇒ 存原始文本、点「采纳 / 作答」时才现解析现落库（2026-11-14 出题同构）。
            step.setResult(JsonUtils.toJsonString(Map.of("answer", r.text())));
        }
        // 阶段 + 一句话输出 + 「要不要人复核」：**写库这一刻才知道**这些东西
        // （段名几个、图几张都要读正文），所以在这里算好存下来，列表直接显示。
        step.setPhase(StringUtils.hasText(step.getPhase()) ? step.getPhase() : GenTaskNodes.PH_WRITE);
        step.setPhaseSeq(step.getPhaseSeq() == null ? step.getSeq() : step.getPhaseSeq());
        step.setOutputSummary(truncate(GenContentValidator.contentSummary(r.text(), retried), 190));
        // 举手的两类：被截断（内容不全）、重写一次仍有结构问题。
        // **不阻断流程** —— 只是让用户知道"这条值得看一眼"。
        step.setNeedConfirm("length".equals(r.finish()) || !leftoverIssues.isEmpty() ? 1 : 0);
        step.setStatus(STEP_OK);
        stepMapper.updateById(step);

        // 第一次调用之后把「这次实际发出去的提示词」指纹写回任务
        // ⚠️ **只写指纹两列**（不整行 updateById）：整行写会把步骤开始前查到的
        // 旧 status 一起写回去 ⇒ 恰好在用户点暂停的那一瞬间被覆盖回 running
        // （这是"暂停失效"的第二个来源）。
        if (task.getPromptMd5() == null && StringUtils.hasText(r.prompt())) {
            task.setPromptLen(r.prompt().length());
            task.setPromptMd5(DigestUtils.md5DigestAsHex(r.prompt().getBytes(StandardCharsets.UTF_8)));
            writePromptFingerprint(task.getId(), task.getPromptLen(), task.getPromptMd5());
        }
    }

    /** 一次模型调用的结果（重试时只保留**最终采用**的那一次） */
    private record CallResult(String text, String finish, String prompt,
                              Integer tokenPrompt, Integer tokenCompletion, int durationMs) {
    }

    /**
     * 这次调用在界面上"问的是什么"。
     *
     * <p>不是小事：问题栏与指令栏必须说的是同一件事，否则模型会两头打架
     * （问题栏"写知识点"、指令是归类规范 ⇒ 输出既不像正文也不像归类结果）。
     *
     * <p>⚠️ **归类的判据是 questionKey**，不是任务类型 ——
     * 任务类型是"这个任务是什么"，questionKey 是"这次调用的提示词是哪条"，
     * 后者才是这次调用真正在做的事（前端可以给一个 theme 任务挂一个归类步骤）。
     */
    private static String questionText(String questionKey) {
        if (Q_PLAN_SKELETON.equals(questionKey)) {
            return "生成目录骨架";
        }
        if (Q_PLAN_EXPAND.equals(questionKey)) {
            return "展开这一章";
        }
        // ⚠️ **出题 / 判卷的问题栏要说这次要它做什么**（2026-11-15 修随堂「出不了题」）：
        // 任务没带 promptKey 时会落到默认的「写知识点」⇒ 模型照着写了一篇知识梳理，
        // 根本不是题（实测过一次）。问题栏与指令必须一致（同归类那条的道理）。
        if (Q_EXAM_PAPER.equals(questionKey)) {
            return "出题";
        }
        return CLASSIFY_QUESTION_KEY.equals(questionKey) ? "补全这条知识" : "写知识点";
    }


    /**
     * 调用一次模型，把流收成完整文本。
     *
     * <p>`maxTokens == null` ⇒ 用后端默认额度（`applyParams` 那套）。
     *
     * @param questionKey 用哪条提示词。写正文走任务自己的 {@code promptKey}；
     *                   AI 归类走固定的 {@code organize:categories,tags}。
     */
    private CallResult callOnce(KpGenTask task, String material, Integer maxTokens, String questionKey) throws IOException {
        AiChatReq req = new AiChatReq();
        // **与前端老链路逐字一致**：那边 `ask('写知识点', …)`，问题这一栏是固定的。
        // 这里若改成知识点标题，模型收到的"用户问题"就变了 ——
        // 新老对比会多一个变量，而且症状是"内容风格不一样"，很难查。
        //
        // ⚠️ **AI 归类例外**：它问的不是"写知识点"，而是"这条该归到哪、叫什么"。
        // 问题这一栏也该换（问题栏写"写知识点"、指令却是归类规范，模型会两头打架）。
        // 目录任务的两种调用同理：问题栏要和这次要它做的事一致。
        req.setQuestion(questionText(questionKey));
        req.setMaterial(material);
        req.setQuestionKey(questionKey);
        applyParams(task, req);
        if (maxTokens != null) {
            req.setMaxTokens(maxTokens);
        }

        StringBuilder body = new StringBuilder();
        String[] prompt = new String[1];
        String[] finish = new String[1];
        Integer[] tokens = new Integer[2];
        long t0 = System.currentTimeMillis();

        aiChatService.stream(task.getUserId(), req, delta -> {
            if (StringUtils.hasText(delta.prompt())) {
                prompt[0] = delta.prompt();
            }
            // ⚠️⚠️ **正文增量绝对不能用 `hasText` 判断**：Spring 的 `hasText` 认为
            // 纯空白字符串（`" "`、`"\n"`）是"空" —— 而流式增量里空格和换行经常单独成帧
            // ⇒ 会把正文里所有空格 / 换行吃掉。用 `isEmpty` 才是真的"没有内容"。
            if (delta.content() != null && !delta.content().isEmpty()) {
                body.append(delta.content());
            }
            if (StringUtils.hasText(delta.finishReason())) {
                finish[0] = delta.finishReason();
            }
            if (delta.tokenPrompt() != null) {
                tokens[0] = delta.tokenPrompt();
            }
            if (delta.tokenCompletion() != null) {
                tokens[1] = delta.tokenCompletion();
            }
        });

        return new CallResult(body.toString(), finish[0], prompt[0],
                tokens[0], tokens[1], (int) (System.currentTimeMillis() - t0));
    }


    /** 参数快照里只认三个影响调用的字段，其余是给界面看的 */
    private void applyParams(KpGenTask task, AiChatReq req) {
        // 端点：读法收敛到 endpointIdOf（建任务校验 / 续跑 / 调用同一条路）
        Long endpointId = endpointIdOf(task);
        if (endpointId != null) {
            req.setEndpointId(endpointId);
        }
        if (!StringUtils.hasText(task.getParams())) {
            return;
        }
        try {
            JsonNode p = JsonUtils.readTree(task.getParams());
            // **`thinking` 不再从任务参数读**（2026-11-11）：界面那个勾已经删了，
            // 思考开关改成按环节的固定策略（`AiStagePolicy`，配置在 sys_config）。
            // 老任务里残留的 `thinking` 字段**故意忽略** —— 否则改一次策略，
            // 历史任务重跑还是按当年的勾走，等于策略没生效。
            if (p.hasNonNull("maxTokens")) {
                req.setMaxTokens(p.get("maxTokens").asInt());
            }
        } catch (Exception e) {
            // 参数坏了不该让整个任务挂：按默认参数继续（提示词/额度兜底是后端自己的事）
            log.warn("任务 {} 的参数快照解析失败：{}", task.getId(), e.getMessage());
        }
    }

    /**
     * 建任务时就验「这次能不能调模型」。
     *
     * <p>⚠️ **为什么要有这一步**（2026-11-16 实跑踩到）：
     * 「还没选好 API」这个错来自 {@code AiEndpointService.requireReady}，
     * 而它是**每一步调用时才判**的 ⇒ 100 条的任务就是 **100 次同样的失败**：
     * 任务在任务中心里显示「部分完成 · 100 条失败」，用户只看到一堆红字，
     * 却完全看不出根因是"没选 API"。
     *
     * <p>⇒ **在跑之前判一次**：端点没配好就**直接拒绝建任务**，
     * 报错一次说清原因，不留一个注定失败的任务在任务中心里。
     *
     * <p>⚠️ **判据只有一份**：这里调的就是 {@code requireReady} 本尊 ——
     * 和真正调用时用的判据同一个（开关开没开 / 这条端点配全没配全），
     * 不另写一份"大概齐了"的判断。
     */
    private void requireEndpointReady(Long userId, KpGenTask task) {
        // 参数坏了按"没选端点"处理（endpointIdOf 静默回 null）⇒ requireReady 给出人话的错误。
        // 抛异常 = 建不了任务。前端那边由 request.js 的响应拦截器统一弹提示。
        aiEndpointService.requireReady(userId, endpointIdOf(task));
    }


    // ==================== 小工具 ====================


    /**
     * params 归一：**对象 / JSON 字符串两种形状都收**，落库统一成 JSON 字符串
     * （2026-11-15 修随堂 400：调用方两种形状都在发，接口不该挑形状）。
     */
    private String normalizeParams(Object params) {
        if (params == null) {
            return null;
        }
        if (params instanceof String s) {
            return s;
        }
        return JsonUtils.toJsonString(params);
    }

    /** 参数里的建结构摘要（前端建完目录后带上来） */
    private String buildSummary(String params) {
        if (!StringUtils.hasText(params)) {
            return "";
        }
        try {
            JsonNode p = JsonUtils.readTree(params);
            JsonNode v = p.get("buildSummary");
            return v == null || v.isNull() ? "" : v.asText("");
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 这个状态是不是"用户要求暂停"（正在暂停 或 已暂停）。
     *
     * <p>⚠️ **只有这一处判"暂停"**：执行器里有两处"看一眼状态就收手"
     * （正文循环、目录展开循环），各写一遍字面量的话，改一处漏一处
     * ⇒ 会出现"正文那条收手了、目录那条还在跑"的半吊子暂停。
     */
    private boolean isPaused(String status) {
        return ST_CANCELING.equals(status) || ST_CANCELED.equals(status);
    }

    /** 现在库里是什么状态（取不到当作"不在"） */
    private String currentStatus(Long taskId) {
        KpGenTask t = taskMapper.selectById(taskId);
        return t == null ? null : t.getStatus();
    }

    /**
     * **收手**：把任务标成「已暂停」并记结束时间。
     *
     * <p>⚠️ **只写 status / finish_time 两列**：整行 updateById 会把
     * 步骤开始前查出来的旧 status 一起写回去（那正是"暂停失效"的根因）。
     */
    private void markPaused(Long taskId) {
        taskMapper.update(null, Wrappers.<KpGenTask>lambdaUpdate()
                .eq(KpGenTask::getId, taskId)
                .set(KpGenTask::getStatus, ST_CANCELED)
                .set(KpGenTask::getFinishTime, LocalDateTime.now()));
    }

    /**
     * **给任务收尾**（成功 / 失败 / 中止）。
     *
     * <p>⚠️ **只写 status / error / finish_time 三列**（不整行 updateById）：
     * 整行写会把查出来那一刻的其它列（尤其 progress 计数）一起写回去，
     * 而那期间执行器可能已经更新过计数 ⇒ 进度被回退一截。
     */
    private void finishTask(Long taskId, String status, String error) {
        taskMapper.update(null, Wrappers.<KpGenTask>lambdaUpdate()
                .eq(KpGenTask::getId, taskId)
                .set(KpGenTask::getStatus, status)
                .set(KpGenTask::getError, error)
                .set(KpGenTask::getFinishTime, LocalDateTime.now()));
    }

    private KpGenTask ownTask(Long id, Long userId) {
        KpGenTask t = taskMapper.selectById(id);
        // 不是你的任务一律当"不存在"，**不区分"不存在"和"不是你的"**（别泄露别人的数据存在性）
        if (t == null || !t.getUserId().equals(userId)) {
            throw new CoreCraftException("任务不存在或已删除");
        }
        return t;
    }

    /**
     * 一个任务的全部步骤（**只查列表要用的列**）。
     *
     * <p>⚠️ **不要 `select *`**：`material`（TEXT，每条约 1KB）和 `prompt_snapshot`
     * （LONGTEXT，实测每条 6~7KB）detail 一个都不用，117 条全查出来就是白传 ~1MB
     * ⇒ 实测 detail 要 **8~9 秒**，而前端全局 timeout 5 秒 ⇒
     * 表现是"展开详情永远停在加载中"（不报错、就是不出来）。
     *
     * <p>`resume()` 也用这个方法：它只改 status，`updateById` 默认不写 null
     * ⇒ 没查的那两个字段不会被覆盖成空。
     *
     * @param withResult **true = 顺带查 result**（AI 归类的产物）。
     *        只有 classify 任务才需要它 —— content 任务的结果在 kp_topic 里，
     *        步骤表那列恒为空，查它只是白撑大 payload。
     */
    private List<KpGenStep> stepsOf(Long taskId, boolean withResult) {
        // ⚠️⚠️ **`.select()` 是「替换」不是「追加」** —— MyBatis-Plus 的
        // `LambdaQueryWrapper#select` 会**覆盖**上一次设的列清单。
        // ⇒ 分两次调（先 13 列，再补一列 result） ⇒ 第二次把前 13 列全冲掉，
        // 查回来的行**只有 result 有值**，其余（id / seq / status / topicId …）全是 null。
        // 症状很隐蔽：详情接口返回 200、步骤数对，但界面上"正在写第几条""哪条失败了"
        // 全是空的，而采纳功能直接废掉（它按 stepId 匹配）。
        // ⇒ **只调一次**，把 result 按条件塞进同一个清单里。
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

    private KpGenTaskResp toResp(KpGenTask t) {
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

    /**
     * 异常 → 给用户看的一句话。
     *
     * <p>⚠️ **上游 4xx 要翻译**：原始消息是「上游返回 402：{一整段 JSON}」，
     * 用户看到只知道他失败了，看不出**该做什么**。
     * 翻译后是「上游账号余额不足，请先充值再试」—— 这才是能动手的话。
     * 原始 JSON 不丢：日志里照旧打全文。
     */
    private static String message(Exception e) {
        if (e instanceof UpstreamHttpException up) {
            return up.toUserMessage();
        }
        String m = e.getMessage();
        return StringUtils.hasText(m) ? m : e.getClass().getSimpleName();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
