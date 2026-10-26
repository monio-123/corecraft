package com.mo.corecraft.service;

import com.mo.corecraft.model.req.SysConfigNodeSaveReq;
import com.mo.corecraft.model.resp.SysConfigNodeResp;

import java.util.List;

/**
 * 配置树：KV 的分组与说明书。
 *
 * **树是展示用的目录，不参与业务**：它只回答"有哪些项、各项叫什么、范围多大、默认值多少"，
 * 值在 sys_config 里，行为（键 → CSS 变量）仍然在代码里。
 */
public interface SysConfigNodeService {

    /** 整棵树（扁平的，前端自己按 parentId 挂） */
    List<SysConfigNodeResp> listAll();

    /** 某个键前缀下的叶子（值类型不为空的那些），按 sort 排。
     *  **keyPrefix 自带结尾的点**（例 {@code "ai.prompt.question."}） */
    List<SysConfigNodeResp> listLeaves(String keyPrefix);

    /** 新建一个节点，返回它的 id */
    Long create(SysConfigNodeSaveReq req);

    /** 改一个节点（短名和父节点不可改） */
    void update(Long id, SysConfigNodeSaveReq req);

    /** 删一个节点：连它的子树和它们各自的键值对一起删 */
    void delete(Long id);
}
