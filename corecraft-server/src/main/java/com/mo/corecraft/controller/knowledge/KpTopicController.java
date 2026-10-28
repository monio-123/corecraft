package com.mo.corecraft.controller.knowledge;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mo.corecraft.model.query.KpTopicQuery;
import com.mo.corecraft.model.req.KpTopicCreateReq;
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
     * <p>筛选项（目录范围 / 类型 / 标签 / 关键词）都在 {@link KpTopicQuery} 里，服务端筛。
     * 目录范围传的是**一串目录 id**（子树由前端算好）——「选中一个目录 = 看整棵子树」
     * 这条规则只有前端那一份，后端再实现一遍就成了两套规则。
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
     */
    @RequestMapping(value = "index", method = RequestMethod.GET)
    public ResultResp<List<KpTopicIndexResp>> index(@RequestParam(value = "q", required = false) String q) {
        Long userId = SecurityUtil.getUserId();
        return ResultResp.data(kpTopicService.selectTopicIndex(userId, q));
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
