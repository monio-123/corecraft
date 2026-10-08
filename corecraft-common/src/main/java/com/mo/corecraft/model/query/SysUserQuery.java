package com.mo.corecraft.model.query;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class SysUserQuery {

    private Long id;

    private String username;

    private String roleCode;
}
