package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 新建一次作答。重做（replay）= {@code replayOfAttemptId} 指向原 attempt，
 * 新建记录、原记录不动（设计 §10.2.1）。
 */
@Getter
@Setter
public class KpAttemptCreateReq {

    @NotNull(message = "paperId不能为空!")
    private Long paperId;

    /** 重做时指向原 attempt；首次作答不传 */
    private Long replayOfAttemptId;
}
