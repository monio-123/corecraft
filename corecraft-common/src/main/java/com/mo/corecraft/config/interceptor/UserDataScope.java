package com.mo.corecraft.config.interceptor;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDataScope {
    private Long userId;
    private Long deptId;
    private List<Long> deptIds;
    private boolean isAdmin;
}