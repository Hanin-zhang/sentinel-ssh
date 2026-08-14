package com.zhanghan.sshproxyproject.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;


@Data
/*
* 后台服务器
* */
@TableName("backend_server")
public class BackendServer {

    //主键，表示服务器id
    @TableId(type = IdType.AUTO)
    private Integer id;

    //服务器名称
    private String serverName;

    private String host;

    private Integer port;

    private String username;

    private String password;

    //在线状态
    private Boolean online = true;

    //当前连接数
    private Integer connectionCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
    private LocalDateTime updateTime;
}
