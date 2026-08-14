package com.zhanghan.sshproxyproject.schedule;

import com.zhanghan.sshproxyproject.entity.BackendServer;
import com.zhanghan.sshproxyproject.service.IBackendServerService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class BackendServerTask {

    @Resource
    private IBackendServerService backendServerService;

    /*
    * Tcp检测服务器端口是否在线，检测3次防止网络抖动
    * */
    @Scheduled(fixedDelay = 360000)
    public void refreshBackendServerStatus(){
        log.info("=====执行服务器在线心跳检测=====");

        int count = 0;

        List<BackendServer> list = backendServerService.list();
        //遍历服务器进行检测
        for(BackendServer server:list){
            boolean res = tryConnect(server.getHost(), server.getPort(), 5000);
            if(!res){
                server.setOnline(false);
            }else {
                server.setOnline(true);
                count+=1;
            }
        }
        log.info("检测执行完成--{}个在线,{}个离线",count,list.size()-count);
    }

    public static boolean tryConnect(String host,int port,int timeoutMs){
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
