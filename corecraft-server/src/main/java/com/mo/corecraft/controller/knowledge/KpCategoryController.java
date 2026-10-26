package com.mo.corecraft.controller.knowledge;

import com.mo.corecraft.model.req.KpCategoryCreateReq;
import com.mo.corecraft.model.req.KpCategoryImportReq;
import com.mo.corecraft.model.req.KpCategoryMoveReq;
import com.mo.corecraft.model.req.KpCategoryRenameReq;
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

    @DeleteMapping("/{id}")
    public ResultResp<Void> delete(@PathVariable("id") Long id) {
        Long userId = SecurityUtil.getUserId();
        kpCategoryService.deleteCategory(id, userId);
        return ResultResp.success();
    }
}
