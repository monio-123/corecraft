package com.mo.corecraft.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SysDictItem extends BaseEntity {

    private Long id;

    @TableField("dict_type_id")
    private Long dictTypeId;

    private String label;

    private String value;

    private Integer sort;

    @TableField("css_class")
    private String cssClass;

    private String remark;

    @TableField("is_enabled")
    private boolean enabled;
}
