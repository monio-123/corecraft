package com.mo.corecraft.enums;

import com.baomidou.mybatisplus.annotation.IEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AccountStatusEnum implements IEnum<Integer> {

    ACTIVE(0),
    DISABLED(1);

    private final int value;

    @Override
    public Integer getValue() {
        return value;
    }

}
