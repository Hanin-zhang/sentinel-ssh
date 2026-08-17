package com.zhanghan.sshproxyproject.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhanghan.sshproxyproject.entity.AiReviewResult;
import com.zhanghan.sshproxyproject.entity.DeepSeekReq;
import com.zhanghan.sshproxyproject.entity.DeepSeekResp;
import com.zhanghan.sshproxyproject.entity.Msg;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * DeepSeek API 调用服务
 * <p>
 * 使用 /v1/chat/completions 端点（兼容 OpenAI 格式），
 * 对 shell 命令进行 AI 安全审查。
 * <p>
 * 调用失败时自动降级放行，不阻塞用户操作。
 */
@Service
@Slf4j
public class DeepSeekService {

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.base-url}")
    private String baseUrl;

    @Value("${deepseek.model}")
    private String model;

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private ObjectMapper objectMapper;

    // ==================== 系统提示词 ====================

    /**
     * 安全审查专家的角色定义
     * 要求 AI 严格返回 JSON，方便程序解析
     */
    private static final String SYSTEM_PROMPT =
            "你是一个 Linux Shell 命令安全审查专家。你的任务是对用户输入的每一条命令进行风险评估。\n" +
            "\n" +
            "## 审查维度\n" +
            "1. 数据破坏：是否会导致数据丢失（rm、dd、mkfs、格式化等）\n" +
            "2. 权限提升：是否尝试提权（sudo、su、chmod 777、setuid 等）\n" +
            "3. 信息泄露：是否将敏感信息外传（curl/wget POST 数据到外部 IP、/etc/passwd 等）\n" +
            "4. 反弹 Shell：是否建立反向连接（bash -i >& /dev/tcp、nc -e、python socket 等）\n" +
            "5. 挖矿/滥用：是否下载运行挖矿程序（xmrig、miner、crypto 等）\n" +
            "6. 后门/持久化：是否创建后门账户、写入 crontab/ssh key、修改启动项\n" +
            "7. 下载+管道执行：是否从网络下载并直接通过管道执行（curl xxx | bash）\n" +
            "8. 服务破坏：是否停止/禁用关键服务（systemctl stop、kill -9、docker rm 等）\n" +
            "\n" +
            "## 返回格式（必须严格遵守，只返回 JSON，不要有其他内容）\n" +
            "{\n" +
            "  \"dangerous\": true/false,\n" +
            "  \"level\": \"HIGH|MEDIUM|LOW|SAFE\",\n" +
            "  \"reason\": \"一句话说明风险原因（中文）\",\n" +
            "  \"category\": \"数据破坏|权限提升|信息泄露|反弹Shell|挖矿|后门|下载执行|服务破坏|正常\"\n" +
            "}\n" +
            "\n" +
            "## 判定标准\n" +
            "- HIGH: 不可逆的破坏性操作，或明确的恶意攻击载荷\n" +
            "- MEDIUM: 有潜在风险但需要进一步确认的操作\n" +
            "- LOW: 有轻微风险或非标准用法，但影响可控\n" +
            "- SAFE: 完全正常的运维/查看命令\n" +
            "\n" +
            "注意：常见的运维命令（ls、cd、cat、less、grep、tail、head、ps、df、free、top、ping、whoami、id、date、echo、exit）即使带正常参数也应判定为 SAFE。";

    /**
     * 安全策略建议生成的角色定义
     * 要求 AI 基于审计统计数据，输出 JSON 数组形式的策略建议
     */
    private static final String RECOMMEND_PROMPT =
            "你是一名 Linux 服务器安全策略专家。我会给你一段 SSH 代理堡垒机的审计统计数据，" +
            "请你基于这些数据，生成 3-6 条安全策略改进建议。\n" +
            "\n" +
            "## 输出要求（必须严格遵守，只返回 JSON 数组，不要有任何其他文字或 markdown 代码块）\n" +
            "[\n" +
            "  {\"level\": \"HIGH|MEDIUM|LOW\", \"content\": \"建议内容（中文，一句话，具体可执行）\"}\n" +
            "]\n" +
            "\n" +
            "## 优先级判定标准\n" +
            "- HIGH: 高危命令频繁出现或存在明确恶意行为，需立即处理\n" +
            "- MEDIUM: 存在一定风险隐患，建议近期整改\n" +
            "- LOW: 常规安全加固建议\n" +
            "\n" +
            "建议要具体、可执行，优先针对统计数据中暴露出来的风险点。";

    // ==================== 公开方法 ====================

    /**
     * 调用 DeepSeek API 审查单条命令
     *
     * @param command  用户执行的 shell 命令
     * @param username 执行用户
     * @param role     用户角色 (admin/operator/guest)
     * @return 审查结果，API 异常时返回 fallback（放行）
     */
    public AiReviewResult reviewCommand(String command, String username, String role) {
        if (command == null || command.isBlank()) {
            return AiReviewResult.fallback();
        }

        // 截断过长命令（token 限制，最长 2000 字符足够覆盖绝大多数场景）
        String truncated = command.length() > 2000 ? command.substring(0, 2000) + "..." : command;

        // 构建请求体
        DeepSeekReq request = buildRequest(truncated, username, role);

        try {
            log.info("调用 DeepSeek 审查命令: user={}, role={}, cmd={}",
                    username, role, truncated.substring(0, Math.min(80, truncated.length())));

            DeepSeekResp response = callDeepSeekApi(request);

            if (response == null || response.getFirstContent() == null) {
                log.warn("DeepSeek 返回空内容");
                return AiReviewResult.fallback();
            }

            String content = response.getFirstContent().trim();
            log.info("DeepSeek 原始返回: {}", content);

            // 尝试解析为 AiReviewResult
            AiReviewResult result = parseReviewResult(content);
            log.info("审查结果: dangerous={}, level={}, category={}, reason={}",
                    result.isDangerous(), result.getLevel(),
                    result.getCategory(), result.getReason());
            return result;

        } catch (RestClientException e) {
            log.error("DeepSeek API 网络异常: {}", e.getMessage());
            return AiReviewResult.fallback();
        } catch (Exception e) {
            log.error("DeepSeek 审查异常, cmd={}", truncated, e);
            return AiReviewResult.fallback();
        }
    }

    /**
     * 调用 DeepSeek API 生成安全策略建议
     * <p>
     * 传入审计统计数据（状态分布 + 危险命令排行榜），由 AI 分析后输出建议列表。
     *
     * @param statistics 审计统计数据（可读文本）
     * @return 建议列表 [{level, content}]，API 异常或解析失败时返回 null（由调用方降级）
     */
    public List<Map<String, String>> generateRecommendations(String statistics) {
        if (statistics == null || statistics.isBlank()) {
            return null;
        }

        DeepSeekReq request = new DeepSeekReq();
        request.setModel(model);
        request.setMessages(List.of(
                new Msg("system", RECOMMEND_PROMPT),
                new Msg("user", "以下是本月的审计统计数据，请据此生成安全策略建议：\n" + statistics)
        ));
        request.setTemperature(0.3);
        request.setMaxTokens(1000);   // 3-6 条建议，1000 token 足够
        request.setStream(false);

        try {
            log.info("调用 DeepSeek 生成策略建议, statsLen={}", statistics.length());
            DeepSeekResp response = callDeepSeekApi(request);

            if (response == null || response.getFirstContent() == null) {
                log.warn("DeepSeek 生成建议返回空内容");
                return null;
            }

            String content = response.getFirstContent().trim();
            log.info("DeepSeek 建议原始返回: {}", content);
            return parseRecommendations(content);

        } catch (RestClientException e) {
            log.error("DeepSeek 生成建议网络异常: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("DeepSeek 生成建议异常", e);
            return null;
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 构建 DeepSeek API 请求
     */
    private DeepSeekReq buildRequest(String command, String username, String role) {
        String userMessage = String.format(
                "用户 %s (角色: %s) 执行了: `%s`",
                username, role, command
        );

        DeepSeekReq req = new DeepSeekReq();
        req.setModel(model);
        req.setMessages(List.of(
                new Msg("system", SYSTEM_PROMPT),
                new Msg("user", userMessage)
        ));
        req.setTemperature(0.1);   // 低温度 → 更确定性的输出
        req.setMaxTokens(256);      // 只需返回 JSON，256 token 足够
        req.setStream(false);
        return req;
    }

    /**
     * 发送 HTTP POST 到 DeepSeek
     */
    private DeepSeekResp callDeepSeekApi(DeepSeekReq request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<DeepSeekReq> entity = new HttpEntity<>(request, headers);

        String url = baseUrl + "/v1/chat/completions";

        ResponseEntity<DeepSeekResp> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                DeepSeekResp.class
        );

        return response.getBody();
    }

    /**
     * 解析 AI 返回的 JSON 为 AiReviewResult
     * <p>
     * 做了多道容错：
     * 1. 尝试直接解析 JSON
     * 2. 如果被 markdown ```json...``` 包裹，去掉后重试
     * 3. 仍失败则基于原始文本做关键词兜底判断
     */
    private AiReviewResult parseReviewResult(String raw) {
        // 1. 去掉可能的 markdown 代码块包装
        String json = raw;
        if (json.startsWith("```")) {
            json = json.replaceAll("^```(?:json)?\\s*", "")
                       .replaceAll("\\s*```$", "")
                       .trim();
        }

        // 2. 尝试 JSON 解析
        try {
            return objectMapper.readValue(json, AiReviewResult.class);
        } catch (JsonProcessingException e) {
            log.warn("DeepSeek 返回非标准 JSON，尝试修复: {}", json);
        }

        // 3. 尝试从文本中提取 JSON 子串（处理 "一些文字 { ... } 一些文字" 的情况）
        try {
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                String extracted = json.substring(start, end + 1);
                return objectMapper.readValue(extracted, AiReviewResult.class);
            }
        } catch (JsonProcessingException e) {
            log.warn("JSON 提取仍然失败");
        }

        // 4. 最终兜底：关键词判断
        return AiReviewResult.rawFallback(raw);
    }

    /**
     * 解析 AI 返回的 JSON 数组为建议列表
     * <p>
     * 做多道容错：
     * 1. 去掉可能的 markdown 代码块包装
     * 2. 尝试直接解析 JSON 数组
     * 3. 从文本中提取 [ ... ] 子串后重试
     * 4. 仍失败返回 null（由调用方降级到默认建议）
     */
    private List<Map<String, String>> parseRecommendations(String raw) {
        String json = raw;
        if (json.startsWith("```")) {
            json = json.replaceAll("^```(?:json)?\\s*", "")
                       .replaceAll("\\s*```$", "")
                       .trim();
        }

        // 1. 直接解析数组
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {});
        } catch (JsonProcessingException e) {
            log.warn("DeepSeek 建议返回非标准 JSON 数组，尝试提取: {}", json);
        }

        // 2. 提取 [ ... ] 子串
        int start = json.indexOf('[');
        int end = json.lastIndexOf(']');
        if (start >= 0 && end > start) {
            try {
                return objectMapper.readValue(json.substring(start, end + 1),
                        new TypeReference<List<Map<String, String>>>() {});
            } catch (JsonProcessingException e) {
                log.warn("提取 AI 建议 JSON 数组仍失败");
            }
        }

        return null;
    }
}
