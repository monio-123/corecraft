package com.mo.corecraft.controller.knowledge;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mo.corecraft.model.req.KpClassifyAdoptReq;
import com.mo.corecraft.model.req.KpGenTaskContinueReq;
import com.mo.corecraft.model.req.KpGenTaskCreateReq;
import com.mo.corecraft.model.resp.KpGenTaskResp;
import com.mo.corecraft.model.resp.ResultResp;
import com.mo.corecraft.service.KpClassifyService;
import com.mo.corecraft.service.KpGenTaskService;
import com.mo.corecraft.utils.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 生成任务中心。
 *
 * <p>**为什么要有它**：几百条正文的循环原来跑在浏览器标签页里，关页面 / 断网就废，
 * 留下一堆只有标题没有正文的知识点。搬到服务端之后，接口只做四件事：
 * 建任务、看进度、停、接着补。
 *
 * <p>任务类型留给以后扩展（出卷 / 判分 / 每日测验）—— 前端任务面板按 `type` 换文案，
 * 不必每加一种能力就做一套进度界面。
 */
@RestController
@RequestMapping("/kp/gen-task")
@RequiredArgsConstructor
public class KpGenTaskController {

    private static final int MAX_PAGE_SIZE = 100;

    private final KpGenTaskService genTaskService;
    private final KpClassifyService classifyService;

    /** 建任务并立刻排队执行。**返回 taskId 就够了**，进度靠轮询 detail / list */
    @PostMapping
    public ResultResp<Long> create(@RequestBody KpGenTaskCreateReq req) {
        return ResultResp.data(genTaskService.create(SecurityUtil.getUserId(), req));
    }

    /** 任务列表（不含每一步明细）。`status` 可选：running / failed / succeeded … */
    @RequestMapping(value = "list", method = RequestMethod.GET)
    public ResultResp<IPage<KpGenTaskResp>> list(@RequestParam(required = false) String status,
                                                 @RequestParam(required = false) String type,
                                                 @RequestParam(defaultValue = "1") Integer page,
                                                 @RequestParam(defaultValue = "20") Integer size) {
        Long userId = SecurityUtil.getUserId();
        int limit = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int current = Math.max(page, 1);
        return ResultResp.data(genTaskService.page(Page.of(current, limit), userId, status, type));
    }

    /** 任务详情（带每一步的状态、耗时、token、finish 原因） */
    @RequestMapping(value = "{id}", method = RequestMethod.GET)
    public ResultResp<KpGenTaskResp> detail(@PathVariable Long id) {
        return ResultResp.data(genTaskService.detail(id, SecurityUtil.getUserId()));
    }

    /**
     * 停止。**正在写的那一条会写完**（不掐流，免得留半截正文），
     * 剩下的不再开始 —— 所以状态可能短暂停在 `canceling`。
     */
    @RequestMapping(value = "{id}/cancel", method = RequestMethod.POST)
    public ResultResp<Void> cancel(@PathVariable Long id) {
        genTaskService.cancel(id, SecurityUtil.getUserId());
        return ResultResp.data(null);
    }

    /** 继续补没写完的（只补 pending / failed 的步骤，成功的不重写） */
    @RequestMapping(value = "{id}/resume", method = RequestMethod.POST)
    public ResultResp<Void> resume(@PathVariable Long id) {
        genTaskService.resume(id, SecurityUtil.getUserId());
        return ResultResp.data(null);
    }

    /**
     * **确认已接手**：目录任务停在 `awaiting` 等人把树建进知识库，
     * 建完之后调它把任务收尾（否则任务中心永远挂着一个「待你确认」）。
     *
     * <p>幂等：不是 awaiting 就直接返回，重复点不会出错。
     */
    @RequestMapping(value = "{id}/ack", method = RequestMethod.POST)
    public ResultResp<Void> ack(@PathVariable Long id) {
        genTaskService.ack(id, SecurityUtil.getUserId());
        return ResultResp.data(null);
    }

    /**
     * **同一个任务继续往下跑**（v2 单任务化）。
     *
     * <p>用户确认完目录之后，正文那一步挂在**同一个任务**上接着跑 ——
     * 而不是新建第二张卡（「生成主题」本来就是一件事）。
     */
    @RequestMapping(value = "{id}/continue", method = RequestMethod.POST)
    public ResultResp<Void> continueTask(@PathVariable Long id,
                                         @RequestBody KpGenTaskContinueReq req) {
        genTaskService.continueTask(id, SecurityUtil.getUserId(), req);
        return ResultResp.data(null);
    }

    /**
     * **重跑某个节点**（节点级重试）。
     *
     * <p>按**节点**（{@code outline} / {@code body} / {@code classify}）重跑，
     * 而不是整个任务重来 —— 目录已经确认过了，没必要再花 4 分钟重出一遍。
     */
    @RequestMapping(value = "{id}/nodes/{nodeKey}/retry", method = RequestMethod.POST)
    public ResultResp<Void> retryNode(@PathVariable Long id, @PathVariable String nodeKey) {
        genTaskService.retryNode(id, SecurityUtil.getUserId(), nodeKey);
        return ResultResp.data(null);
    }

    /**
     * **删除任务**（软删）。
     *
     * <p>⚠️ **只删任务本身**：已经写进知识库的正文留在库里 ——
     * "删掉这个任务"≠"删掉生成的知识"。
     */
    @RequestMapping(value = "{id}", method = RequestMethod.DELETE)
    public ResultResp<Void> remove(@PathVariable Long id) {
        genTaskService.remove(id, SecurityUtil.getUserId());
        return ResultResp.data(null);
    }

    /**
     * **采纳 AI 归类的结果**（写库）。
     *
     * <p>⚠️ **这是唯一一处把 AI 归类的建议写进 kp_topic 的地方** ——
     * 跑任务的时候只存建议（{@code kp_gen_step.result}），一个字都不写库。
     * 理由：目录归属是**需要人工确认的主观判断**。
     *
     * <p>前端传的是**采纳那一刻解析出来的目录 id**（不是 AI 原始的路径字符串）：
     * 任务跑完到人点采纳之间目录可能变了 ⇒ 传 id 才谈得上"按当下清单重新校验"。
     */
    @RequestMapping(value = "classify/adopt", method = RequestMethod.POST)
    public ResultResp<Integer> adoptClassify(@Valid @RequestBody KpClassifyAdoptReq req) {
        Long userId = SecurityUtil.getUserId();
        return ResultResp.data(classifyService.adopt(req, userId));
    }
}
