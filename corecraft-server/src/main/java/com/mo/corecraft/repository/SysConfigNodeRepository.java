package com.mo.corecraft.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mo.corecraft.mapper.SysConfigNodeMapper;
import com.mo.corecraft.model.entity.SysConfigNode;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 配置树节点的读取 / 落库。
 *
 * **刻意不继承 AbstractRepository**：那套 Query + list + page 是给**集合型**资源准备的。
 * 这张表的动作只有三种：整棵树读出来、按键取一个、按 id 增删改。
 * 树本身只有几十个节点，**整棵读出来在内存里拼**比一层一层查省事得多。
 */
@Repository
public class SysConfigNodeRepository {

    private final SysConfigNodeMapper mapper;

    public SysConfigNodeRepository(SysConfigNodeMapper mapper) {
        this.mapper = mapper;
    }

    /** 整棵树（按 sort、再按 id 排，顺序稳定） */
    public List<SysConfigNode> listAll() {
        return mapper.selectList(new LambdaQueryWrapper<SysConfigNode>()
                .orderByAsc(SysConfigNode::getSort)
                .orderByAsc(SysConfigNode::getId));
    }

    public SysConfigNode getById(Long id) {
        return mapper.selectById(id);
    }

    public SysConfigNode getByKey(String configKey) {
        return mapper.selectOne(new LambdaQueryWrapper<SysConfigNode>()
                .eq(SysConfigNode::getConfigKey, configKey));
    }

    public int insert(SysConfigNode entity) {
        return mapper.insert(entity);
    }

    /** 只写要改的那几列（**规则见 {@link AbstractRepository#update}**，只存那一处） */
    public int update(SysConfigNode entity) {
        return mapper.updateById(entity);
    }

    public int deleteById(Long id) {
        return mapper.deleteById(id);
    }
}
