package com.zhanghan.sshproxyproject.core.server;

import com.zhanghan.sshproxyproject.entity.BackendServer;
import com.zhanghan.sshproxyproject.service.IBackendServerService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/*
* 实现轮询访问后台服务器
* */
@Slf4j
@Component
@Data
public class BackendManager {

    @Resource
    private IBackendServerService backendServerService;

    //后台服务器列表
    public final List<BackendServer> serverList = new ArrayList<>();

    //原子计数器，确保线程安全
    private final AtomicInteger index = new AtomicInteger(0);

    private Integer curIndex ;

    //初始化列表
    @PostConstruct
    public void init(){
        log.info("初始化后台服务器列表");
        List<BackendServer> list = backendServerService.list();
        serverList.addAll(list);
    }

    //使用泛型,轮询
    public <T> T select(List<T> list){
        int cur = index.getAndIncrement() % list.size();
        log.info("分配服务器id{}",cur);
        curIndex = cur;
        return  list.get(cur);
    }

    //获取当前id
    public Integer getId() {
        int i = 1;
        return curIndex;
    }

}
