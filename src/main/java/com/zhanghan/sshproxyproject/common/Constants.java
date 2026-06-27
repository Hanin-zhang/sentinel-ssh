package com.zhanghan.sshproxyproject.common;

import java.util.List;
import java.util.Set;

public class Constants {
    public final static String ServerKeyPath ="./server-keys";

    public final static Set<String> BLOCK_COMMAND = Set.of(
            "rm -rf",
            "mkfs",
            "fdisk",
            "dd if",
            "wget",
            "curl",
            "nohup",
            "reboot",
            "shutdown",
            "halt",
            "poweroff",
            "useradd",
            "usermod",
            "passwd",
            "chmod 777",
            "chown -R",
            "docker",
            "yum install",
            "apt install",
            "xmrig",
            "miner",
            "xmr",
            "crypto",
            "nc ",
            "netcat",
            "socat",
            "nmap",
            "bash <(",
            "| bash");

    public static final String MYADMIN = "admin";
    public static final String MYOPERATOR = "operator";
    public static final String MYGUEST = "guest";

    public static final int ONLINE = 1;

    public static final int NOT_ONLINE = 0;
}
