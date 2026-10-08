package com.mo.corecraft.model.query;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SysDictTypeQuery {

    private Long id;

    private String code;

    private String name;

    private Boolean enabled;
}
