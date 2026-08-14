package com.zhanghan.sshproxyproject.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * DeepSeek API 完整响应体
 * 对应 POST https://api.deepseek.com/v1/chat/completions 的返回
 */
@Data
public class DeepSeekResp {

    private String id;
    private String object;
    private Long created;
    private String model;
    private List<Choice> choices;
    private Usage usage;

    // ---- 内嵌类 ----

    @Data
    public static class Choice {
        private Integer index;
        private Message message;

        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Data
    public static class Message {
        private String role;
        private String content;
    }

    @Data
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        @JsonProperty("completion_tokens")
        private Integer completionTokens;

        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }

    // ---- 便捷方法 ----

    /**
     * 提取第一个 choice 的文本内容，没有则返回 null
     */
    public String getFirstContent() {
        if (choices != null && !choices.isEmpty()
                && choices.get(0).getMessage() != null) {
            return choices.get(0).getMessage().getContent();
        }
        return null;
    }
}
