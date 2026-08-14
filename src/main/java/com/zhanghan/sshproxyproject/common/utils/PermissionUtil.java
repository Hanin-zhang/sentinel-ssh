package com.zhanghan.sshproxyproject.common.utils;

import com.zhanghan.sshproxyproject.entity.User;
import com.zhanghan.sshproxyproject.service.IUserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;
import com.zhanghan.sshproxyproject.common.Constants;
import org.springframework.stereotype.Component;

import static com.zhanghan.sshproxyproject.common.Constants.*;

/*
* 权限校验 — 三层防线第一层（命令名级别，O(1) Set 查找）
* <p>
* <b>分层安全模型：</b>
* <ol>
 *   <li><b>角色命令名白名单（本类）</b> — 按角色限制可执行的命令名集合，O(1) 查找
 *       admin → 全部 / operator → 运维工具集 / guest → 只读诊断</li>
 *   <li><b>静态规则引擎（StaticRuleEngine）</b> — 基于正则匹配命令参数，判断 BLOCK / ALLOW / SUSPICIOUS</li>
 *   <li><b>AI 异步审查（DeepSeekService）</b> — 对灰区 SUSPICIOUS 命令做语义级二次确认</li>
 * </ol>
 * <p>
 * <b>说明：</b>本类只判断"命令名是否在角色白名单中"，不检查参数。
 * 参数的合法性由 StaticRuleEngine（同步，&lt;1ms）+ DeepSeek AI（异步）两层共同保证。
 * 例如：operator 可以执行 {@code rm}，但 {@code rm -rf /} 会被静态规则 BLOCK。
 */
@Slf4j
public class PermissionUtil {

    //定义角色可执行的命令（仅检查命令名，参数级危险由 StaticRuleEngine + AI 审查兜底）
    // admin: 所有命令（角色检查直接通过，不在此处列举）
    private static final Set<String> ADMINCOMMAND = Set.of("*");

    // operator (ops): 运维人员 — 完整的运维工具集
    private static final Set<String> OPERATORCOMMAND = Set.of(
            // 基础
            "exit", "logout", "clear", "history",
            // 文件浏览
            "ls", "dir", "pwd", "cd", "tree",
            // 文件查看
            "cat", "less", "more", "head", "tail", "file", "stat", "wc", "nl", "od",
            // 文本搜索/处理
            "grep", "egrep", "fgrep", "awk", "sed", "cut", "sort", "uniq", "tr", "diff", "comm", "join", "paste",
            // 文件操作
            "mkdir", "touch", "cp", "mv", "rm", "rmdir", "ln", "tee", "install",
            // 权限
            "chmod", "chown", "chgrp", "umask",
            // 系统监控
            "ps", "top", "htop", "df", "du", "free", "uptime", "who", "whoami", "id",
            "date", "uname", "hostname", "dmesg", "lscpu", "lsblk", "lsmem", "lsusb", "lspci",
            "dmidecode", "iostat", "vmstat", "sar", "mpstat",
            // 网络诊断/工具
            "ping", "traceroute", "tracepath", "nslookup", "dig", "host",
            "ss", "netstat", "ip", "ifconfig", "curl", "wget", "ftp", "sftp",
            // 进程管理
            "kill", "pkill", "killall", "pgrep", "pidof", "pstree", "nice", "renice",
            // 服务管理
            "systemctl", "journalctl", "service",
            // 包管理
            "apt", "apt-get", "yum", "dnf", "rpm", "dpkg", "pip", "pip3", "npm", "snap",
            // 归档/压缩
            "tar", "gzip", "gunzip", "bzip2", "bunzip2", "zip", "unzip", "zcat", "xz", "unxz",
            // 容器
            "docker", "kubectl", "podman",
            // 版本控制
            "git", "svn",
            // 编辑器
            "vi", "vim", "nano", "emacs", "ed",
            // 远程连接/文件传输
            "ssh", "scp", "rsync", "telnet", "mtr",
            // 脚本/语言解释器
            "python", "python3", "bash", "sh", "perl", "ruby", "php", "node", "java",
            // 输出/环境
            "echo", "printf", "yes", "env", "printenv", "export", "unset", "alias", "unalias",
            // 帮助
            "man", "info", "whatis", "which", "whereis", "type", "apropos",
            // 搜索/查找
            "find", "locate", "xargs",
            // 后台/会话
            "nohup", "screen", "tmux", "watch",
            // 定时任务
            "crontab", "at", "batch",
            // 认证/密码
            "passwd", "chage",
            // 文件系统
            "mount", "umount",
            // 安全/加密
            "openssl", "gpg", "md5sum", "sha1sum", "sha256sum", "sha512sum", "base64", "xxd",
            // 权限提升（通过静态规则 + AI 审查审计每个 sudo 调用）
            "sudo",
            // 防火墙管理（通过静态规则防止误用）
            "iptables", "nft"
    );

    // guest: 只读观察者 — 仅可查看，不可修改
    private static final Set<String> GUESTCOMMAND = Set.of(
            // 基础
            "exit", "logout", "clear", "history",
            // 目录浏览
            "ls", "dir", "pwd", "cd",
            // 文件查看（只读）
            "cat", "less", "more", "head", "tail", "file", "stat", "wc",
            // 文本搜索（只读）
            "grep", "egrep", "fgrep",
            // 查找（只读，-exec 由静态规则拦截）
            "find",
            // 系统信息（只读）
            "ps", "top", "df", "du", "free", "uptime", "who", "whoami", "id",
            "date", "uname", "hostname", "lscpu", "lsblk",
            // 网络诊断（只读）
            "ping", "traceroute", "nslookup", "dig", "host", "ss", "netstat",
            // 输出/环境
            "echo", "printf", "env", "printenv",
            // 帮助
            "man", "info", "whatis", "which", "whereis", "type", "apropos"
    );


    //通过角色判断该命令是否能使用
    public static boolean checkUserPermission(User user,String command){
        if(command == null || command.isBlank()){
            return false;
        }
        String role = user.getRole();
        // 只取命令第一个词（如 rm -rf → rm）
        String cmd = command.trim().split("\\s+")[0];
        log.info("检查的语句,{}:{}",command,cmd);
        return switch (role){
            case MYADMIN -> true;
            case MYOPERATOR -> OPERATORCOMMAND.contains(cmd);
            case MYGUEST -> GUESTCOMMAND.contains(cmd);
            default -> false;
        };
    }

}
