package com.zhanghan.sshproxyproject.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/*
* 日志记录表
* */
@Data
@TableName("audit_log")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;        // 用户ID

    private Integer serverId;      // 服务器ID

    private String clientIp;    // 客户端IP

    private String serverHost;  // 服务器IP

    private Integer serverPort; // 服务器端口

    private String username;    // 用户名

    private String command;     // 执行命令

    private Integer status;     // 0正常 1拦截

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
