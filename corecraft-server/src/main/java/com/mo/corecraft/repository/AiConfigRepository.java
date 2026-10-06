package com.mo.corecraft.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mo.corecraft.mapper.AiConfigMapper;
import com.mo.corecraft.model.entity.AiConfig;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * AI 配置的读取 / 落库。
 *
 * **刻意不继承 AbstractRepository**：那套 Query + list + page 是给**集合型**资源
 * （用户、角色、字典项）准备的——它们要分页、要模糊查、要按多字段筛。
 * 配置表一辈子只有两个动作：按 user_id 读一行、把这行写回去（开关只存不删）。
 * 给它套一层列表抽象，是"为了和别的表长得一样"而多写三倍代码。
 *
 * 哪天它真的需要分页或检索了，再换回 AbstractRepository，成本也就三个方法。
 */
@Repository
public class AiConfigRepository {

    private final AiConfigMapper mapper;

    public AiConfigRepository(AiConfigMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<AiConfig> findByUserId(Long userId) {
        var wrapper = new LambdaQueryWrapper<AiConfig>().eq(AiConfig::getUserId, userId);
        return Optional.ofNullable(mapper.selectOne(wrapper));
    }

    public int insert(AiConfig entity) {
        return mapper.insert(entity);
    }

    public int update(AiConfig entity) {
        return mapper.updateById(entity);
    }
}