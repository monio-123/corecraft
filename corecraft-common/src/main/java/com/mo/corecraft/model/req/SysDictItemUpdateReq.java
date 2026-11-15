package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SysDictItemUpdateReq {

    @NotNull(message = "id不能为空!")
    private Long id;

    private Long dictTypeId;

    private String label;

    private String value;

    private Integer sort;

    private String cssClass;

    private String remark;

    /**
     * 该类型默认是否参与掌握度：null=**不修改**（保持原值）/ true=参与 / false=不参与。
     *
     * <p>⚠️ **这里的 null 是"不改"而不是"设成继承"**：与 {@link KpTopic.masteryEnabled}
     * 的语义不同——单条覆盖可以靠 ALWAYS 策略把 null 写进去（那列的 null 有含义），
     * 而这一列的 null 是"没这个配置项"，当作"改成继承"会静默抹掉管理员的设置。
     */
    private Boolean masteryEnabled;

    private Boolean enabled;
}
