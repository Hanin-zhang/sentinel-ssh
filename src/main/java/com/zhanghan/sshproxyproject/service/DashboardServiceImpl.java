package com.zhanghan.sshproxyproject.service;

import com.zhanghan.sshproxyproject.dto.Result;
import com.zhanghan.sshproxyproject.entity.BackendServer;
import com.zhanghan.sshproxyproject.entity.DashboardData;
import com.zhanghan.sshproxyproject.mapper.AuditLogMapper;
import com.zhanghan.sshproxyproject.mapper.DashboardMapper;
import com.zhanghan.sshproxyproject.session.SessionManager;
import com.zhanghan.sshproxyproject.vo.BackendServerVO;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.zhanghan.sshproxyproject.session.SessionManager.getDangerCmdNum;
import static com.zhanghan.sshproxyproject.session.SessionManager.getOnlineNum;

@Slf4j
@Service
public class DashboardServiceImpl implements IDashboardService {

    @Resource
    private IUserService userService;
    @Resource
    private AuditLogMapper auditLogMapper;
    @Resource
    private DashboardMapper dashboardMapper;
    @Resource
    private IBackendServerService backendServerService;

    private final AtomicLong totalCmdNum = new AtomicLong(0);
    private final AtomicLong totalDangerCmdNum = new AtomicLong(0);

    // 缓存最新 DashboardData
    private volatile DashboardData latestData;

    // ✅ 项目启动后，依赖注入完成，再查数据库总数
    @PostConstruct
    public void initTotalNum() {
        //获取总命令的数量以及危险命令数量
        long totalCmd = auditLogMapper.getTotalCmdNum();
        long totalDangerCmd = auditLogMapper.getTotalDangerCmdNum();

        totalCmdNum.set(totalCmd);
        totalDangerCmdNum.set(totalDangerCmd);

        log.info("============初始化仪表盘总数成功：总命令={}，危险命令={}============", totalCmd, totalDangerCmd);
    }

    /*
    * 前端实现时要配合短轮询
    * */
    @Override
    public Result getData() {
        return Result.ok(latestData);
    }

    //刷新数据
    @Scheduled(fixedDelay = 3000)
    public void refreshData(){
        try {
            log.info("=========刷新仪表盘数据=========");
            //获取在线用户
            Integer onlineNum = getOnlineNum();
            //获取总用户数量
            long totalUserNum = userService.count();
            //获取今日命令数量
            Long cmdNum = auditLogMapper.countByToday(LocalDate.now());
            //获取今日危险命令数量
            long dangerCmdNum = getDangerCmdNum();

            // 2. 累加入全局总数（线程安全）
            totalCmdNum.addAndGet(cmdNum);
            totalDangerCmdNum.addAndGet(dangerCmdNum);

            //封装首页数据
            latestData = DashboardData.builder()
                    .onlineNum(onlineNum)
                    .totalUserNum(totalUserNum)
                    .todayCmdNum(cmdNum)
                    .todayDangerCmdNum(dangerCmdNum)
                    .totalCmdNum(totalCmdNum)
                    .totalDangerCmdNum(totalDangerCmdNum)
                    .build();
        } catch (Exception e) {
            log.error("刷新仪表盘数据失败",e);
        }
    }

    /*
    * 获取服务器列表
    * */
    @Override
    public Result getServers() {
        List<BackendServer> servers = backendServerService.list();

        List<BackendServerVO> vos = new ArrayList<>();

        for(BackendServer server:servers){
            BackendServerVO vo = new BackendServerVO();
            BeanUtils.copyProperties(server,vo);

            vos.add(vo);
        }

        return Result.ok(vos);
    }
}
