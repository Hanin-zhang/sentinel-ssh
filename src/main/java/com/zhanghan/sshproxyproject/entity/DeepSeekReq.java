package com.zhanghan.sshproxyproject.entity;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// 请求体
@Data
public class DeepSeekReq {
    private String model;

    private List<Msg> messages;

    private Double temperature = 0.1;

    @JsonProperty("max_tokens")
    private Integer maxTokens = 100;

    private Boolean stream = false;
}

