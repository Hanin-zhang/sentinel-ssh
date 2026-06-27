package com.zhanghan.sshproxyproject.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhanghan.sshproxyproject.dto.PageQueryDTO;
import com.zhanghan.sshproxyproject.dto.Result;
import com.zhanghan.sshproxyproject.entity.AuditLog;
import com.zhanghan.sshproxyproject.mapper.AuditLogMapper;
import com.zhanghan.sshproxyproject.vo.AuditLogVO;
import io.netty.util.internal.StringUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.print.attribute.standard.PageRanges;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class AuditLogServiceImpl extends ServiceImpl<AuditLogMapper, AuditLog> implements IAuditLogService {

    @Resource
    private AuditLogMapper auditLogMapper;

    @Override
    public Result mypage(PageQueryDTO pageQueryDTO) {

        Page<AuditLog> page = new Page<>(pageQueryDTO.getPage(), pageQueryDTO.getPageSize());

        LambdaQueryWrapper<AuditLog> wrapper = Wrappers.lambdaQuery();
        wrapper
                // 模糊查询name
                .like(StringUtils.hasText(pageQueryDTO.getUsername()), AuditLog::getUsername, pageQueryDTO.getUsername())
                // 精确匹配status
                .eq(pageQueryDTO.getStatus() != null, AuditLog::getStatus, pageQueryDTO.getStatus())
                // 精确匹配serverId
                .eq(pageQueryDTO.getServerId() != null, AuditLog::getServerId, pageQueryDTO.getServerId())
                // 创建时间 >= 开始时间
                .ge(pageQueryDTO.getBeginTime() != null, AuditLog::getCreateTime, pageQueryDTO.getBeginTime())
                // 创建时间 <= 结束时间
                .le(pageQueryDTO.getEndTime() != null, AuditLog::getCreateTime, pageQueryDTO.getEndTime())
                // 根据创建时间倒序排序
                .orderByDesc(AuditLog::getCreateTime);

        Page<AuditLog> logList = auditLogMapper.selectPage(page, wrapper);

        List<AuditLog> list = logList.getRecords();

        List<AuditLogVO> logVOS = new ArrayList<>();
        for(AuditLog log:list){
            AuditLogVO vo = AuditLogVO.builder()
                    .id(log.getId())
                    .status(log.getStatus())
                    .serverId(log.getServerId())
                    .username(log.getUsername())
                    .clientIp(log.getClientIp())
                    .command(log.getCommand())
                    .createTime(log.getCreateTime())
                    .serverHost(log.getServerHost())
                    .riskLevel(computeRiskLevel(log.getCommand(), log.getStatus()))
                    .build();

            logVOS.add(vo);
        }

        long total = logList.getTotal();

        return Result.ok(logVOS,total);
    }

    @Override
    public Result getDetailById(Long id) {
        AuditLog log = auditLogMapper.selectById(id);
        if (log == null) {
            return Result.fail("审计日志不存在");
        }
        AuditLogVO vo = AuditLogVO.builder()
                .id(log.getId())
                .status(log.getStatus())
                .serverId(log.getServerId())
                .username(log.getUsername())
                .clientIp(log.getClientIp())
                .command(log.getCommand())
                .createTime(log.getCreateTime())
                .serverHost(log.getServerHost())
                .riskLevel(computeRiskLevel(log.getCommand(), log.getStatus()))
                .build();
        return Result.ok(vo);
    }

    /**
     * 根据命令内容智能计算风险等级
     */
    private String computeRiskLevel(String command, Integer status) {
        if (command == null) return "LOW";
        // 被拦截的命令直接标记为高风险
        if (status != null && status == 1) return "HIGH";

        String lower = command.toLowerCase().trim();

//        // 高危：删除/格式化/权限全开/清空防火墙
//        if (lower.matches(".*rm\\s+-rf.*") || lower.matches(".*rm\\s+-r\\s+/.*")
//                || lower.matches(".*dd\\s+if=.*") || lower.contains("mkfs.")
//                || lower.matches(".*:\(\)\\s*\\{.*") || lower.contains("chmod 777")
//                || lower.contains("iptables -f") || lower.contains("> /dev/sda")) {
//            return "HIGH";
//        }

        // 中危：sudo/权限变更/用户管理/容器操作/kill
        if (lower.contains("sudo ") || lower.contains("passwd ")
                || lower.contains("useradd ") || lower.contains("usermod ")
                || lower.contains("chown ") || lower.contains("kill -9")
                || lower.contains("docker rm") || lower.contains("docker exec")
                || lower.contains("kubectl exec") || lower.contains("ssh -")
                || lower.contains("iptables ") || lower.matches(".*wget.*\\|.*sh.*")
                || lower.matches(".*curl.*\\|.*sh.*") || lower.matches(".*\\./.*\\.sh.*")) {
            return "MEDIUM";
        }

        return "LOW";
    }
}
