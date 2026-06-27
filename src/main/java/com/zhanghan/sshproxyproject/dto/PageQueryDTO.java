package com.zhanghan.sshproxyproject.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class PageQueryDTO {

    private String username;

    private Integer page = 1;

    private Integer pageSize = 20;//设计时有（5、10、20、50、100）这几个分页选项

    //是否被拦截
    private Integer status;

    //服务器id
    private Integer serverId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime beginTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime;
}
