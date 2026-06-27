package com.zhanghan.sshproxyproject.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
* 命令处理类，对用户输入的命令进行审查、拦截
* */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CommandProcessor {
    //是否因危险而拦截
    private boolean blocked = false;

    //是否因权限而拦截
    private boolean permitted = false;

    //是否连接失败
    private boolean disconnect;

    //返回的消息
    private String message;
}
