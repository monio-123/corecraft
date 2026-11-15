package com.mo.corecraft.controller.knowledge;

import com.mo.corecraft.model.req.KpCategoryCreateReq;
import com.mo.corecraft.model.req.KpCategoryImportReq;
import com.mo.corecraft.model.req.KpCategoryMoveReq;
import com.mo.corecraft.model.req.KpCategoryRenameReq;
import com.mo.corecraft.model.resp.KpCategoryDeletePreviewResp;
import com.mo.corecraft.model.resp.KpCategoryResp;
import com.mo.corecraft.model.resp.ResultResp;
import com.mo.corecraft.service.KpCategoryService;
import com.mo.corecraft.utils.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/kp/category")
@RequiredArgsConstructor
public class KpCategoryController {

    private final KpCategoryService kpCategoryService;

    @GetMapping("/list")
    public ResultResp<List<KpCategoryResp>> list() {
        Long userId = SecurityUtil.getUserId();
        return ResultResp.data(kpCategoryService.listCategories(userId));
    }

    @PostMapping
    public ResultResp<Void> create(@Valid @RequestBody KpCategoryCreateReq req) {
        Long userId = SecurityUtil.getUserId();
        kpCategoryService.createCategory(req, userId);
        return ResultResp.success();
    }

    /**
     * 批量导入目录树：一次提交整棵树，服务端在一个事务里建完。
     *
     * <p>单个建目录那个接口留着（建一两个目录用它更直接）。
     *
     * <p>返回这批目录的 id：调用方要拿它把知识点挂到具体目录下。
     */
    @PostMapping("/import")
    public ResultResp<List<KpCategoryResp>> importTree(@Valid @RequestBody KpCategoryImportReq req) {
        Long userId = SecurityUtil.getUserId();
        return ResultResp.success(kpCategoryService.importTree(req, userId));
    }

    @PutMapping("/rename")
    public ResultResp<Void> rename(@Valid @RequestBody KpCategoryRenameReq req) {
        Long userId = SecurityUtil.getUserId();
        kpCategoryService.renameCategory(req, userId);
        return ResultResp.success();
    }

    @PutMapping("/move")
    public ResultResp<Void> move(@Valid @RequestBody KpCategoryMoveReq req) {
        Long userId = SecurityUtil.getUserId();
        kpCategoryService.moveCategory(req.getId(), req.getParentId(), userId);
        return ResultResp.success();
    }

    /**
     * 「删这个目录会连带删掉什么」的**预检**（不删任何东西）。
     *
     * <p>确认框里那个「N 条知识点」必须来自服务端现查：前端那份结构索引是
     * **当前关键词下的快照**，还会过期 —— 数错了用户就是"以为删 3 条、实际删 30 条"。
     */
    @GetMapping("/{id}/delete-preview")
    public ResultResp<KpCategoryDeletePreviewResp> deletePreview(@PathVariable("id") Long id) {
        Long userId = SecurityUtil.getUserId();
        return ResultResp.data(kpCategoryService.previewDelete(id, userId));
    }

    /**
     * 删目录（**整棵子树一并删**）。
     *
     * @param cascadeTopics `true` = 连这棵子树里的知识点一起删（**软删**，数据还在库里）。
     *        不传 = 知识点回到"未归类"，一条不删（默认）。
     *        <p>⚠️ **默认不删**：目录只是归类的壳，整理结构不该默认丢知识。
     *        但"删目录 → 一批知识点全掉进待归类"是**堆积源**（越整理越脏），
     *        所以给一个明确的开关让用户自己决定。
     *        <p>它是**查询参数**而不是 body：这是个 DELETE 请求，body 里带语义不清
     *        （多数 HTTP 客户端对 DELETE body 支持参半），query 参数最省事也最通用。
     */
    @DeleteMapping("/{id}")
    public ResultResp<Void> delete(@PathVariable("id") Long id,
                                   @RequestParam(value = "cascadeTopics", defaultValue = "false") boolean cascadeTopics) {
        Long userId = SecurityUtil.getUserId();
        kpCategoryService.deleteCategory(id, userId, cascadeTopics);
        return ResultResp.success();
    }
}
