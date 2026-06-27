package com.zhanghan.sshproxyproject.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ConnectWay {

    //选择的方式(默认轮询)(AUTO/MANUAL)
    private String mode = "AUTO";

    //选择的服务器id
    private Integer serverId;
}
