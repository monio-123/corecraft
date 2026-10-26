package com.mo.corecraft.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mo.corecraft.mapper.SysConfigMapper;
import com.mo.corecraft.model.entity.SysConfig;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 系统配置的读取 / 落库。
 *
 * **刻意不继承 AbstractRepository**：那套 Query + list + page 是给**集合型**资源
 * （用户、角色、字典项）准备的——要分页、要模糊查。这张配置表的动作只有两三种：
 * 按 user_id 取一批、按 (user_id, config_key) 写回。给它套一层列表抽象，
 * 是"为了和别的表长得一样"而多写代码。哪天它真需要检索了再换回去也不迟。
 *
 * **每个方法都带 userId**：把"这一行归谁"留在方法签名里，
 * 免得某个调用点忘了带 userId 就动到了别人的行上。
 */
@Repository
public class SysConfigRepository {

    private final SysConfigMapper mapper;

    public SysConfigRepository(SysConfigMapper mapper) {
        this.mapper = mapper;
    }

    /** 某个 user_id 名下的行（user_id=0 即系统默认）；prefix 非空时再按键前缀过滤 */
    public List<SysConfig> listByUserId(Long userId, String prefix) {
        var wrapper = new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getUserId, userId);
        filterByPrefix(wrapper, prefix);
        return mapper.selectList(wrapper);
    }

    /** 各用户的覆盖行（user_id>0）；userId 给了就再按它精确过滤 */
    public List<SysConfig> listUserScope(Long userId, String prefix) {
        var wrapper = new LambdaQueryWrapper<SysConfig>().gt(SysConfig::getUserId, 0L);
        if (userId != null) {
            wrapper.eq(SysConfig::getUserId, userId);
        }
        filterByPrefix(wrapper, prefix);
        return mapper.selectList(wrapper);
    }

    /** 某用户这些键的现有行：批量保存前先问一次"这条是新建还是更新" */
    public List<SysConfig> listByUserIdAndKeys(Long userId, Collection<String> keys) {
        if (keys.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getUserId, userId)
                .in(SysConfig::getConfigKey, keys));
    }

    public int insert(SysConfig entity) {
        return mapper.insert(entity);
    }

    /** 只写要改的那几列（**规则见 {@link AbstractRepository#update}**，只存那一处） */
    public int update(SysConfig entity) {
        return mapper.updateById(entity);
    }

    /** 删某个用户某个键的那一行 */
    public int deleteByUserIdAndKey(Long userId, String key) {
        return mapper.delete(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getUserId, userId)
                .eq(SysConfig::getConfigKey, key));
    }

    /** 删某个用户某个前缀下的所有行（= 恢复系统默认） */
    public int deleteByUserIdAndKeyPrefix(Long userId, String prefix) {
        return mapper.delete(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getUserId, userId)
                .likeRight(SysConfig::getConfigKey, prefix));
    }

    /**
     * 删某个键和它下面所有前缀的行（**不分 user_id**）。
     *
     * 用于"删配置节点时连带删它的值"：节点没了，键就没人认领，
     * 留着只是看不见的垃圾。系统默认和各用户的覆盖一起清掉。
     *
     * 前缀是「键 + 一个点」：ui.side 匹配 ui.side.sideWidth，
     * 但**不会**误伤 ui.sideWidth 这种只差一个字符的别的键。
     */
    public int deleteByKeyOrPrefix(String key) {
        return mapper.delete(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key)
                .or().likeRight(SysConfig::getConfigKey, key + "."));
    }

    /** 前缀过滤。空前缀 = 不过滤（"不按前缀筛"和"按空串筛"是两件事） */
    private void filterByPrefix(LambdaQueryWrapper<SysConfig> wrapper, String prefix) {
        if (prefix != null && !prefix.isBlank()) {
            wrapper.likeRight(SysConfig::getConfigKey, prefix);
        }
    }
}
