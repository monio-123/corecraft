package com.mo.corecraft.controller.knowledge;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mo.corecraft.model.query.KpTopicQuery;
import com.mo.corecraft.model.req.KpTopicCreateReq;
import com.mo.corecraft.model.req.KpTopicMasteryReq;
import com.mo.corecraft.model.req.KpTopicUpdateReq;
import com.mo.corecraft.model.resp.KpTopicIndexResp;
import com.mo.corecraft.model.resp.KpTopicResp;
import com.mo.corecraft.model.resp.ResultResp;
import com.mo.corecraft.service.KpTopicService;
import com.mo.corecraft.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/kp/topic")
@RequiredArgsConstructor
public class KpTopicController {

    /** 一页最多给多少条。分页的意义就是"别一次拉那么多"，
     *  不封顶的话 size=99999 就把限制绕过去了 ⇒ 白做。 */
    private static final int MAX_PAGE_SIZE = 200;

    private final KpTopicService kpTopicService;

    /**
     * 列表页的知识点：**分页**返回，带正文。
     *
     * <p>筛选项（目录范围 / 类型 / 标签 / 关键词 / 时间区间）都在 {@link KpTopicQuery} 里，服务端筛。
     * 目录范围传的是**一串目录 id**（子树由前端算好）——「选中一个目录 = 看整棵子树」
     * 这条规则只有前端那一份，后端再实现一遍就成了两套规则。
     *
     * <p>时间区间（{@code createdFrom} / {@code createdTo}）也是前端算好传过来的 ——
     * 它是**列表页的范围**（今天 / 本周 / 本月），分页在服务端 ⇒ 这两个条件必须在服务端。
     */
    @RequestMapping(value = "list", method = RequestMethod.GET)
    public ResultResp<IPage<KpTopicResp>> list(KpTopicQuery query,
                                                @RequestParam(defaultValue = "1") Integer page,
                                                @RequestParam(defaultValue = "20") Integer size) {
        Long userId = SecurityUtil.getUserId();
        query.setUserId(userId);
        // 默认值只写在 defaultValue 里（缺参数和空串都走它）⇒ 这两个值**不会是 null**，
        // 不用再判一次。封顶是必须的：不封顶的话 size=99999 就把"分页"绕过去了。
        int limit = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int current = Math.max(page, 1);
        return ResultResp.data(kpTopicService.selectTopicPage(Page.of(current, limit), query));
    }

    /**
     * 结构索引：全部知识点的**轻量投影，不含正文**。
     *
     * <p>目录树（要在知识点那一层显示标题）、各处按类型/标签/有无正文分类计数 ——
     * 这些都要"全集"，但一条正文都不需要。实测读全文 4416ms、不读正文 78ms。
     *
     * <p>带 q 时返回的是**关键词下的全集**：浏览器那边按它算分面计数，
     * 数字才和"点进去看到多少条"对得上。
     *
     * <p>⚠️ **scope 决定搜哪一块**（标题 / 正文 / 都搜，见 KpTopicQuery）：
     * 前端两个搜索模式各传一个 —— **「搜正文」那档传 `both`**（标题 + 正文都搜，
     * 用户 2026-11-05：「对于卡片栏搜索模式 搜索的包括正文和正文标题」）、
     * **「搜目录」那档传 `title`**（只搜知识点标题；**目录名在前端本地匹配**，
     * 不往服务端跑一趟）⇒ 索引里那批就是**这一档的命中集**，
     * 前端拿它算目录命中、算分面计数。认不出来的按 both 算（默认那条语义不变）。
     */
    @RequestMapping(value = "index", method = RequestMethod.GET)
    public ResultResp<List<KpTopicIndexResp>> index(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "scope", required = false) String scope) {
        Long userId = SecurityUtil.getUserId();
        return ResultResp.data(kpTopicService.selectTopicIndex(userId, q, scope));
    }

    @RequestMapping(value = "{id}", method = RequestMethod.GET)
    public ResultResp<KpTopicResp> detail(@PathVariable("id") Long id) {
        // 归属校验和上面 list 走同一处：两条路径以前不一致，详情那条漏了
        Long userId = SecurityUtil.getUserId();
        return ResultResp.data(kpTopicService.selectTopicById(id, userId));
    }

    @RequestMapping(value = "", method = RequestMethod.POST)
    public ResultResp<Void> create(@Validated @RequestBody KpTopicCreateReq req) {
        Long userId = SecurityUtil.getUserId();
        kpTopicService.createTopic(req, userId);
        return ResultResp.success();
    }

    @RequestMapping(value = "", method = RequestMethod.PUT)
    public ResultResp<Void> update(@Validated @RequestBody KpTopicUpdateReq req) {
        Long userId = SecurityUtil.getUserId();
        kpTopicService.updateTopic(req, userId);
        return ResultResp.success();
    }

    /**
     * 设置这条内容的**掌握度参与开关**（2026-11-12）。
     *
     * <p>三态：{@code enabled=true} 参与 / {@code false} 不参与 / {@code null} 恢复继承类型默认。
     *
     * <p>⚠️ **为什么单独一个端点**（不塞进 PUT /kp/topic）：
     * 通用更新那条路是"保存正文"，前端每次都不带这个字段 ⇒ 它的 null 是"没这个字段"；
     * 而这里的 null 是"恢复继承"这个**明确动作**。两者塞在一起就必须靠"字段存不存在"
     * 来区分，而 JSON 里"没传"和"传 null"在很多客户端上根本分不开。
     */
    @RequestMapping(value = "{id}/mastery", method = RequestMethod.PUT)
    public ResultResp<Void> setMastery(@PathVariable("id") Long id,
                                       @RequestBody(required = false) KpTopicMasteryReq body) {
        Long userId = SecurityUtil.getUserId();
        KpTopicMasteryReq req = body == null ? new KpTopicMasteryReq() : body;
        req.setId(id);
        kpTopicService.setTopicMasteryEnabled(req, userId);
        return ResultResp.success();
    }

    @RequestMapping(value = "{id}", method = RequestMethod.DELETE)
    public ResultResp<Void> delete(@PathVariable("id") Long id) {
        Long userId = SecurityUtil.getUserId();
        kpTopicService.deleteTopic(id, userId);
        return ResultResp.success();
    }

    @RequestMapping(value = "{id}/relation/{relatedId}", method = RequestMethod.POST)
    public ResultResp<Void> addRelation(@PathVariable("id") Long id,
                                        @PathVariable("relatedId") Long relatedId) {
        kpTopicService.addTopicRelation(id, relatedId);
        return ResultResp.success();
    }

    @RequestMapping(value = "{id}/relation/{relatedId}", method = RequestMethod.DELETE)
    public ResultResp<Void> removeRelation(@PathVariable("id") Long id,
                                           @PathVariable("relatedId") Long relatedId) {
        kpTopicService.removeTopicRelation(id, relatedId);
        return ResultResp.success();
    }

}
