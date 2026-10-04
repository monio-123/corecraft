package com.mo.corecraft.controller.knowledge;

import com.mo.corecraft.model.req.KpCategoryCreateReq;
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
        Long userId = SecurityUtil.getUser().getSysUserDTO().getId();
        return ResultResp.data(kpCategoryService.listCategories(userId));
    }

    @PostMapping
    public ResultResp<Void> create(@Valid @RequestBody KpCategoryCreateReq req) {
        Long userId = SecurityUtil.getUser().getSysUserDTO().getId();
        kpCategoryService.createCategory(req, userId);
        return ResultResp.success();
    }

    @PutMapping("/rename")
    public ResultResp<Void> rename(@Valid @RequestBody KpCategoryRenameReq req) {
        Long userId = SecurityUtil.getUser().getSysUserDTO().getId();
        kpCategoryService.renameCategory(req, userId);
        return ResultResp.success();
    }

    @DeleteMapping("/{id}")
    public ResultResp<Void> delete(@PathVariable("id") Long id) {
        Long userId = SecurityUtil.getUser().getSysUserDTO().getId();
        kpCategoryService.deleteCategory(id, userId);
        return ResultResp.success();
    }
}
