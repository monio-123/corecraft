package com.mo.corecraft.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mo.corecraft.mapper.AiEndpointMapper;
import com.mo.corecraft.model.entity.AiEndpoint;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * AI 接口的读取 / 落库。
 *
 * 和 AiConfigRepository 同一个理由不继承 AbstractRepository：这里只有
 * 「按 user_id 列一批」和「按 id 读写删」，没有分页也没有模糊检索。
 *
 * **每个方法都带 userId**：别写成 findById(Long id) 就完事——那是把"能不能动这一条"
 * 的判断挪到调用方，任何一个忘了带 userId 的调用点都是越权读别人的密钥。
 */
@Repository
public class AiEndpointRepository {

    private final AiEndpointMapper mapper;

    public AiEndpointRepository(AiEndpointMapper mapper) {
        this.mapper = mapper;
    }

    public List<AiEndpoint> listByUserId(Long userId) {
        return mapper.selectList(new LambdaQueryWrapper<AiEndpoint>()
                .eq(AiEndpoint::getUserId, userId)
                .orderByAsc(AiEndpoint::getId));
    }

    public Optional<AiEndpoint> findByIdAndUserId(Long id, Long userId) {
        var wrapper = new LambdaQueryWrapper<AiEndpoint>()
                .eq(AiEndpoint::getId, id)
                .eq(AiEndpoint::getUserId, userId);
        return Optional.ofNullable(mapper.selectOne(wrapper));
    }

    public int insert(AiEndpoint entity) {
        return mapper.insert(entity);
    }

    public int update(AiEndpoint entity) {
        return mapper.updateById(entity);
    }

    public int deleteByIdAndUserId(Long id, Long userId) {
        var wrapper = new LambdaQueryWrapper<AiEndpoint>()
                .eq(AiEndpoint::getId, id)
                .eq(AiEndpoint::getUserId, userId);
        return mapper.delete(wrapper);
    }
}