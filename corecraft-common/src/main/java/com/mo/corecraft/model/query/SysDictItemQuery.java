package com.mo.corecraft.model.query;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SysDictItemQuery {

    private Long id;

    private Long dictTypeId;

    private String label;

    private String value;

    private Boolean enabled;
}
