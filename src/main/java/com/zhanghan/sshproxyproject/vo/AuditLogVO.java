package com.zhanghan.sshproxyproject.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AuditLogVO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer serverId;      // 服务器ID

    private String clientIp;    // 客户端IP

    private String serverHost;  // 服务器IP

    private String username;    // 用户名

    private String command;     // 执行命令

    private Integer status;     // 0正常 1拦截

    private String riskLevel;   // HIGH / MEDIUM / LOW（由命令内容计算得出）

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
