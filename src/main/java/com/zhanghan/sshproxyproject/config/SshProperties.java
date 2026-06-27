package com.zhanghan.sshproxyproject.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ssh")
@Data
public class SshProperties {

    //公钥地址
    private String publicKeyPath;

    //超时时间
    private int timeout;
}
