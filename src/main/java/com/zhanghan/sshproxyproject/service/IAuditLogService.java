package com.zhanghan.sshproxyproject.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhanghan.sshproxyproject.dto.PageQueryDTO;
import com.zhanghan.sshproxyproject.dto.Result;
import com.zhanghan.sshproxyproject.entity.AiReviewResult;
import com.zhanghan.sshproxyproject.entity.AuditLog;
import com.zhanghan.sshproxyproject.entity.BackendServer;
import com.zhanghan.sshproxyproject.entity.User;

public interface IAuditLogService extends IService<AuditLog> {
    Result mypage(PageQueryDTO pageQueryDTO);

    Result getDetailById(Long id);

    /**
     * 记录 AI 判定为 HIGH 的高危命令审计日志
     * <p>
     * status 标记为 2（AI 高危），区别于 0 正常 / 1 静态拦截 / 3 AI 中危
     *
     * @param command  原始命令
     * @param user     执行用户
     * @param server   目标后端服务器
     * @param userIp   用户客户端 IP
     * @param aiResult AI 审查结果（含 reason、category）
     */
    void saveHighRisk(String command, User user, BackendServer server, String userIp, AiReviewResult aiResult);

    /**
     * 记录 AI 判定为 MEDIUM 的中危命令审计日志
     * <p>
     * status 标记为 3（AI 中危），静默记录不做实时告警
     *
     * @param command  原始命令
     * @param user     执行用户
     * @param server   目标后端服务器
     * @param userIp   用户客户端 IP
     * @param aiResult AI 审查结果
     */
    void saveMediumRisk(String command, User user, BackendServer server, String userIp, AiReviewResult aiResult);
}
