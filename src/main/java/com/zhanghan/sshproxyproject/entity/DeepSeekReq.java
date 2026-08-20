package com.zhanghan.sshproxyproject.entity;


import com.fasterxml.jackson.annotation.JsonInclude;
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

    /**
     * 是否开启思考模式（Qwen 3.x 系列默认开启）。
     * 命令审查（千问）在 buildRequest 中显式置为 false，只返回 JSON 更快更稳；
     * 默认 null 时该字段不出现在请求体，避免把千问专属参数误传给 DeepSeek。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("enable_thinking")
    private Boolean enableThinking;
}

