package com.mo.corecraft.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mo.corecraft.enums.PermissionTypeEnum;
import com.mo.corecraft.model.entity.SysPermission;
import com.mo.corecraft.model.query.SysPermissionQuery;
import com.mo.corecraft.model.req.SysPermissionCreateReq;
import com.mo.corecraft.model.req.SysPermissionUpdateReq;
import com.mo.corecraft.model.resp.SysPermissionResp;
import com.mo.corecraft.repository.SysPermissionRepository;
import com.mo.corecraft.service.SysPermissionService;
import com.mo.corecraft.utils.BeanUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SysPermissionServiceImpl implements SysPermissionService {

    private final SysPermissionRepository sysPermissionRepository;

    @Override
    public SysPermissionResp selectSysPermission(SysPermissionQuery query) {
        return sysPermissionRepository.get(query, BeanUtils.converter(SysPermissionResp.class));
    }

    @Override
    public List<SysPermissionResp> selectSysPermissionList(SysPermissionQuery query) {
        return sysPermissionRepository.list(query, BeanUtils.converter(SysPermissionResp.class));
    }

    @Override
    public IPage<SysPermissionResp> selectSysPermissionPage(Page<SysPermission> page, SysPermissionQuery query) {
        return sysPermissionRepository.page(page, query, BeanUtils.converter(SysPermissionResp.class));
    }

    @Override
    public List<SysPermissionResp> selectSysPermissionTree(SysPermissionQuery query) {
        List<SysPermissionResp> list = selectSysPermissionList(query);
        return toTree(list);
    }

    @Override
    public List<SysPermissionResp> selectMenuTreeByPermissions(Collection<String> permissionCodes, boolean includeAll) {
        SysPermissionQuery query = new SysPermissionQuery();
        query.setEnabled(true);
        List<SysPermissionResp> list = selectSysPermissionList(query).stream()
                .filter(p -> p.getType() == PermissionTypeEnum.MENU || p.getType() == PermissionTypeEnum.GROUP)
                .toList();
        List<SysPermissionResp> roots = toTree(list);
        if (includeAll) {
            return roots;
        }
        Set<String> codes = permissionCodes == null ? Set.of() : Set.copyOf(permissionCodes);
        return roots.stream()
                .map(node -> pruneByCodes(node, codes))
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public void createSysPermission(SysPermissionCreateReq req) {
        SysPermission entity = new SysPermission();
        entity.setParentId(req.getParentId());
        entity.setCode(req.getCode());
        entity.setName(req.getName());
        entity.setSort(Optional.ofNullable(req.getSort()).orElse(0));
        entity.setEnabled(Optional.ofNullable(req.getEnabled()).orElse(true));
        entity.setType(req.getType());
        entity.setMeta(req.getMeta());
        sysPermissionRepository.insert(entity);
    }

    @Override
    public void updateSysPermission(SysPermissionUpdateReq req) {
        sysPermissionRepository.update(req.getId(), req, (r, e) -> {
            if (r.getParentId() != null) {
                e.setParentId(r.getParentId());
            }
            if (StringUtils.isNotBlank(r.getCode())) {
                e.setCode(r.getCode());
            }
            if (StringUtils.isNotBlank(r.getName())) {
                e.setName(r.getName());
            }
            if (r.getSort() != null) {
                e.setSort(r.getSort());
            }
            if (r.getEnabled() != null) {
                e.setEnabled(r.getEnabled());
            }
            if (r.getType() != null) {
                e.setType(r.getType());
            }
            if (r.getMeta() != null) {
                e.setMeta(r.getMeta());
            }
        });
    }

    @Override
    public void deleteSysPermission(Long id) {
        sysPermissionRepository.delete(id);
    }

    @Override
    public void deleteBatchSysPermission(List<Long> ids) {
        sysPermissionRepository.deleteBatch(ids);
    }

    private static List<SysPermissionResp> toTree(List<SysPermissionResp> list) {
        Map<Long, SysPermissionResp> idMap = new HashMap<>();
        for (SysPermissionResp node : list) {
            if (node.getId() != null) {
                idMap.put(node.getId(), node);
            }
            node.setChildren(null);
        }

        List<SysPermissionResp> roots = new ArrayList<>();
        for (SysPermissionResp node : list) {
            Long parentId = node.getParentId();
            if (parentId != null && idMap.containsKey(parentId)) {
                SysPermissionResp parent = idMap.get(parentId);
                if (parent.getChildren() == null) {
                    parent.setChildren(new ArrayList<>());
                }
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }

        sortTree(roots);
        return roots;
    }

    private static void sortTree(List<SysPermissionResp> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        nodes.sort((a, b) -> {
            int sa = Optional.ofNullable(a.getSort()).orElse(0);
            int sb = Optional.ofNullable(b.getSort()).orElse(0);
            if (sa != sb) {
                return Integer.compare(sa, sb);
            }
            long ia = Optional.ofNullable(a.getId()).orElse(0L);
            long ib = Optional.ofNullable(b.getId()).orElse(0L);
            return Long.compare(ia, ib);
        });
        for (SysPermissionResp n : nodes) {
            sortTree(n.getChildren());
        }
    }

    private static SysPermissionResp pruneByCodes(SysPermissionResp node, Set<String> codes) {
        if (node == null) {
            return null;
        }
        List<SysPermissionResp> children = node.getChildren();
        if (children != null && !children.isEmpty()) {
            List<SysPermissionResp> kept = children.stream()
                    .map(child -> pruneByCodes(child, codes))
                    .filter(Objects::nonNull)
                    .toList();
            node.setChildren(kept.isEmpty() ? null : new ArrayList<>(kept));
        } else {
            node.setChildren(null);
        }

        boolean selfAllowed = StringUtils.isNotBlank(node.getCode()) && codes.contains(node.getCode());
        boolean hasChildren = node.getChildren() != null && !node.getChildren().isEmpty();
        if (selfAllowed || hasChildren) {
            return node;
        }
        return null;
    }
}
