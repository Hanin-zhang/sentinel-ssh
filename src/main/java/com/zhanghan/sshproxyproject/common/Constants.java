package com.zhanghan.sshproxyproject.common;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class Constants {
    public final static String ServerKeyPath = "./server-keys";

    // ==================== 命令审查规则（静态规则引擎） ====================

    /**
     * 【已废弃】旧版简单子串黑名单
     * <p>
     * 使用 {@code String.contains()} 匹配，存在严重误报问题（例如 echo "rm -rf" 也会被拦截）。
     * 请使用下方的 {@link #BLOCK_PATTERNS} 和 {@link #SUSPICIOUS_PATTERNS}，它们基于正则，
     * 匹配精准度更高、误报率更低。
     */
    @Deprecated
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

    // ==================== 静态规则：白名单快速放行（ALLOW） ====================

    /**
     * 白名单正则规则 — 明确安全的命令，命中后直接放行，不进入 SUSPICIOUS 检查
     * <p>
     * <b>设计目的：</b>
     * <ul>
     *   <li>减少灰区误报 — 常见运维命令不应进入 AI 审查</li>
     *   <li>快速通道 — 在 BLOCK 之后、SUSPICIOUS 之前匹配</li>
     *   <li><b>铁律：所有 ALLOW 规则必须排除 {@code >  >>  <  |  ;  &}，
     *       防止通过重定向/管道覆写系统文件的攻击被快速放行</b></li>
     *   <li>保守原则 — 只收录"纯只读且无重定向"的命令模式；
     *       能修改文件的命令（vi/dd/cp/mv）一律不放入 ALLOW</li>
     * </ul>
     * <p>
     * <b>注意：</b>匹配顺序为 BLOCK → ALLOW → SUSPICIOUS → 默认放行。
     * 已在 BLOCK 规则中明确拦截的高危命令不会被此白名单放行。
     */
    public static final List<RuleEntry> ALLOW_PATTERNS = List.of(
            // ---- 纯只读文件浏览（绝对安全） ----
            rule("ls/dir/pwd/cd 目录浏览", "(?i)^\\s*(ls|dir|pwd|cd|tree)\\b(\\s+-[a-zA-Z]+)?\\s*$"),
            // 注意: [^|;&><] 排除了重定向和管道，防止 cat > /etc/... 覆写系统文件绕过审查
            rule("cat/less/more 文件查看", "(?i)^\\s*(cat|less|more|head|tail|nl|od)\\s+[^-][^|;&><]*$"),
            rule("file/stat/wc 文件信息", "(?i)^\\s*(file|stat|wc|du)\\s+[^|;&><]*$"),

            // ---- 纯只读文本搜索/处理 ----
            rule("grep 文本搜索", "(?i)^\\s*(grep|egrep|fgrep)\\s+"),
            // awk/sed 已从 ALLOW 移除：
            //   sed -i 可原地修改文件（如 sed -i 's/x//' /etc/shadow），不应快速放行
            //   awk 内部支持 > 文件写入重定向，同样不适合 ALLOW
            //   二者走默认路径，由审计日志记录

            // ---- 纯只读系统信息 ----
            rule("进程查看", "(?i)^\\s*(ps|top|htop|pgrep|pidof|pstree)\\b"),
            rule("系统状态信息", "(?i)^\\s*(df|free|uptime|who|whoami|id|date|uname|hostname|arch)\\b"),
            rule("硬件信息", "(?i)^\\s*(lscpu|lsblk|lsmem|lspci|lsusb|dmidecode)\\b"),
            rule("内核日志", "(?i)^\\s*dmesg\\b"),

            // ---- 纯只读网络诊断 ----
            rule("ping 连通测试", "(?i)^\\s*ping\\s+-c\\s+\\d+"),
            rule("DNS 查询", "(?i)^\\s*(nslookup|dig|host)\\s+"),
            rule("网络连接查看", "(?i)^\\s*(ss|netstat)\\s+-[ntuap]+"),
            rule("IP 地址查看", "(?i)^\\s*ip\\s+(addr|link|route|neigh)\\s+(show|list|get)\\b"),

            // ---- 纯只读服务/容器状态 ----
            rule("systemctl 状态查看", "(?i)^\\s*systemctl\\s+(status|is-enabled|is-active|list-units|list-timers|show|cat)\\b"),
            rule("journalctl 日志查看", "(?i)^\\s*journalctl\\s+"),
            rule("docker 只读命令", "(?i)^\\s*docker\\s+(ps|images|logs|inspect|stats|info|version|history|diff|system\\s+df)\\b"),
            rule("kubectl 只读命令", "(?i)^\\s*kubectl\\s+(get|describe|logs|top|explain|api-resources|cluster-info|config\\s+view|auth\\s+can-i)\\b"),

            // ---- 纯只读版本控制 ----
            rule("git 只读操作", "(?i)^\\s*git\\s+(status|log|diff|show|branch|fetch|remote|stash\\s+list|tag\\s+-l)\\b"),

            // ---- 帮助/工具 ----
            rule("帮助手册", "(?i)^\\s*(man|info|whatis|apropos|which|whereis|type)\\s+"),
            // 注意: vi/vim/nano 已从 ALLOW 移除（可编辑系统文件，走默认路径记录审计日志）
            // 注意: [^|;&><] 排除重定向/管道，防止 echo xxx > /etc/crontab 覆写系统文件
            rule("echo/printf 输出", "(?i)^\\s*(echo|printf)\\s+[^|;&><]*$"),
            rule("终端控制", "(?i)^\\s*(clear|reset|history|exit|logout)\\s*$"),

            // ---- SSH/SCP 正常连接（不含隧道参数 -D/-R/-L） ----
            rule("scp 文件传输", "(?i)^\\s*scp\\s+(-[rPvCp]+\\s+)*[^|;&]+\\s+[^|;&]+\\s*$"),

            // ---- 包管理查询（只读） ----
            rule("apt 查询", "(?i)^\\s*apt(?:-get)?\\s+(list|search|show|policy|check)\\b"),
            rule("yum/dnf 查询", "(?i)^\\s*(yum|dnf)\\s+(list|info|search|check-update|repolist|history)\\b"),

            // ---- 归档查看 ----
            rule("tar 列出内容", "(?i)^\\s*tar\\s+-[t]+[vf]"),
            rule("压缩文件查看", "(?i)^\\s*(unzip|gunzip|bunzip2)\\s+-[lt]")
    );

    // ==================== 静态规则：明确拦截（BLOCK） ====================

    /**
     * 高危命令正则规则 — 命中即拦截
     * <p>
     * 规则设计原则：
     * <ul>
     *   <li>不可逆的破坏性操作（如 rm -rf /、mkfs、dd 覆盖）</li>
     *   <li>明确的恶意攻击载荷（如反弹 Shell、挖矿程序）</li>
     *   <li>系统级破坏（如 reboot、shutdown、halt）</li>
     * </ul>
     * 每条规则包含名称 + 正则，匹配后直接返回 BLOCK。
     */
    public static final List<RuleEntry> BLOCK_PATTERNS = List.of(
            // ================================================================
            // 数据破坏
            // ================================================================
            rule("rm -rf 系统关键目录", "(?i)rm\\s+(-[rRf]+\\s)+/(bin|sbin|etc|boot|lib|lib64|root|sys|usr|home|var|opt|tmp)(\\s|$)"),
            rule("rm -rf 根目录", "(?i)rm\\s+(-[rRf]+\\s)+/(\\s|$)"),
            rule("mkfs 格式化磁盘", "(?i)\\bmkfs\\.?(ext[234]|xfs|btrfs|fat|ntfs|vfat)?"),
            rule("dd 覆写磁盘/分区", "(?i)\\bdd\\s+if=.+of=/dev/(sd[a-z]\\d*|nvme\\d+n\\d+|mapper/|md\\d+|loop\\d+|hda|hdb|vda|vdb|xvda|xvdb)"),
            rule("覆写 /etc 关键文件(重定向)", "(?i)(>|>>)\\s*/etc/(passwd|shadow|sudoers|group|gshadow|hosts|resolv\\.conf|fstab|crontab|systemd/system/\\w+\\.service)"),
            rule("cat/tee 覆写系统关键文件", "(?i)\\b(cat|tee|dd|cp)\\s+\\S+\\s*(>|>>)\\s*/(etc|bin|sbin|boot|usr/bin|usr/sbin)/(passwd|shadow|sudoers|crontab|fstab|resolv\\.conf|systemd/system/\\w+)"),
            rule("chmod 777 系统关键目录", "(?i)\\bchmod\\s+(-R\\s+)?777\\s+/(etc|bin|sbin|boot|usr|var|opt|home|root)"),
            rule("mv 覆盖系统文件", "(?i)\\bmv\\s+(-f\\s+)?\\S+\\s+/(etc|bin|sbin|boot|lib|lib64)/(passwd|shadow|sudoers|fstab|resolv\\.conf)"),
            rule("chattr 锁定/解锁系统文件", "(?i)\\bchattr\\s+[-+]i\\s+/(etc|bin|sbin|boot|usr|var)"),

            // ================================================================
            // 系统破坏
            // ================================================================
            rule("reboot 重启系统", "(?i)^\\s*(reboot|systemctl\\s+reboot|init\\s+6)\\b"),
            rule("shutdown 关机", "(?i)^\\s*(shutdown|poweroff|halt|systemctl\\s+(poweroff|halt)|init\\s+0)\\b"),
            rule("fdisk/parted 磁盘分区操作", "(?i)^\\s*(fdisk|parted|sfdisk|cfdisk|gdisk)\\s+/dev/"),
            rule("Fork 炸弹", "(?i):\\(\\)\\s*\\{\\s*:\\|:&\\s*\\}\\s*;\\s*:"),
            rule("sysctl 内核危险参数", "(?i)\\bsysctl\\s+-w\\s+kernel\\.(panic|sysrq|core_pattern|panic_on_oops)"),

            // ================================================================
            // 反弹 Shell（高置信度签名 — 多语言覆盖）
            // ================================================================
            rule("bash 反弹 Shell", "(?i)bash\\s+-i\\s*[>&]\\s*/dev/tcp/"),
            rule("nc 反弹 Shell (-e)", "(?i)\\bnc\\s+.*-e\\s+/bin/(ba)?sh"),
            rule("nc 反弹 Shell (管道)", "(?i)\\bnc\\s+\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\s+\\d+\\s*\\|\\s*/bin/(ba)?sh"),
            rule("python 反弹 Shell", "(?i)python3?\\s+-c\\s+['\"](import\\s+(socket|os|subprocess|pty)|exec\\()"),
            rule("perl 反弹 Shell", "(?i)\\bperl\\s+-[eE]\\s+['\"]use\\s+Socket"),
            rule("ruby 反弹 Shell", "(?i)\\bruby\\s+-[eE]\\s+['\"]require\\s+['\"]socket"),
            rule("php 反弹 Shell", "(?i)\\bphp\\s+-r\\s+['\"]\\$sock\\s*=\\s*fsockopen"),
            rule("lua 反弹 Shell", "(?i)\\blua\\s+-e\\s+['\"].*socket\\.(tcp|connect)"),
            rule("awk 反弹 Shell", "(?i)\\bawk\\s+['\"]BEGIN\\s*\\{.*\\/inet\\/"),
            rule("telnet 反弹 Shell", "(?i)\\btelnet\\s+\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\s+\\d+\\s*\\|"),

            // ================================================================
            // 挖矿/恶意程序
            // ================================================================
            rule("xmrig 挖矿程序", "(?i)\\b(xmrig|xmrstak|cpuminer|ccminer|ethminer|t-rex|phoenixminer|lolminer|nbminer)\\b"),
            rule("crypto-miner 关键词", "(?i)\\b(cryptonight|minerd?|stratum\\+tcp|nicehash|miningpool)"),
            rule("挖矿配置文件", "(?i)(config\\.json| pools\\.txt| miner\\.cfg).*xmrig"),

            // ================================================================
            // 后门/持久化
            // ================================================================
            rule("写入 SSH authorized_keys", "(?i)(>>?|tee\\s+-a)\\s+(~?/?.ssh/authorized_keys|/home/\\w+/\\\\.ssh/authorized_keys|/root/\\\\.ssh/authorized_keys)"),
            rule("添加 crontab 后门(echo)", "(?i)echo\\s+.*>>\\s*/etc/crontab"),
            rule("crontab 导入文件", "(?i)\\bcrontab\\s+/(tmp|dev/shm|var/tmp|home/\\w+/\\w+)\\b"),
            rule("crontab -e 交互编辑", "(?i)\\bcrontab\\s+-e\\b"),
            rule("写入 rc.local 启动脚本", "(?i)(>>?|tee\\s+-a)\\s+/etc/(rc\\.local|rc\\d\\.d/|init\\.d/)"),
            rule("systemd 后门服务", "(?i)(>>?|tee)\\s+/etc/systemd/system/\\w+\\.service"),
            rule("LD_PRELOAD 劫持", "(?i)\\b(export\\s+LD_PRELOAD=|LD_PRELOAD=\\S+\\s)"),

            // ================================================================
            // 内核/驱动操控
            // ================================================================
            rule("modprobe 加载内核模块", "(?i)\\bmodprobe\\s+(?!-r\\b|-\\-remove\\b)[a-zA-Z]"),
            rule("挂载覆盖系统目录(bind mount)", "(?i)\\bmount\\s+--bind\\s+\\S+\\s+/(etc|bin|sbin|usr/bin|usr/sbin|lib|lib64)"),

            // ================================================================
            // 关键服务破坏
            // ================================================================
            rule("停止安全/审计服务", "(?i)\\bsystemctl\\s+(stop|disable|mask)\\s+(firewalld|iptables|ufw|selinux|apparmor|auditd|rsyslog|fail2ban)")
    );

    // ==================== 静态规则：可疑/灰区（SUSPICIOUS） ====================

    /**
     * 可疑命令正则规则 — 命中后进入灰区，先放行再异步 AI 审查
     * <p>
     * 规则设计原则：
     * <ul>
     *   <li>命令本身可能合法也可能恶意，取决于上下文（如 curl 下载脚本）</li>
     *   <li>工具本身不是恶意，但参数组合可疑（如 docker 挂载宿主机根目录）</li>
     *   <li>行为模式异常但不是 100% 确认攻击（如 SSH 隧道、iptables 修改）</li>
     * </ul>
     */
    public static final List<RuleEntry> SUSPICIOUS_PATTERNS = List.of(
            // ================================================================
            // 下载 + 管道执行（灰区核心）
            // ================================================================
            rule("curl 下载并管道执行", "(?i)\\bcurl\\s+.*\\|\\s*(ba)?sh"),
            rule("wget 下载并管道执行", "(?i)\\bwget\\s+.*-O\\s*-\\s*\\|\\s*(ba)?sh"),
            rule("curl/wget 下载到临时目录并执行", "(?i)(curl|wget).*/(tmp|dev/shm|var/tmp|run).*\\.(sh|bin|elf|py|pl|rb|go|out)"),

            // ================================================================
            // 可疑网络工具用法
            // ================================================================
            rule("nc/netcat 监听端口", "(?i)\\b(nc|netcat|ncat)\\s+-[lL]\\w*p\\s+\\d+"),
            rule("socat 高级转发/执行", "(?i)\\bsocat\\s+.*(EXEC|SYSTEM|TCP-LISTEN|UDP-LISTEN|OPEN)"),
            rule("nmap 主动扫描", "(?i)\\bnmap\\s+(-s[STUA]|-O|-A|-p\\s|--script)"),
            rule("masscan 高速扫描", "(?i)\\bmasscan\\s+"),

            // ================================================================
            // 可疑容器操作
            // ================================================================
            rule("docker 挂载宿主机根目录", "(?i)\\bdocker\\s+(run|exec|create).*-v\\s+/:"),
            rule("docker 特权模式运行", "(?i)\\bdocker\\s+(run|create)\\s+.*(--privileged|--cap-add=ALL|--cap-add=SYS_ADMIN|--security-opt\\s+label:disable)"),
            rule("docker 挂载 Docker Socket", "(?i)\\bdocker\\s+run.*-v\\s+/var/run/docker\\.sock"),
            rule("docker 特权容器逃逸", "(?i)\\bdocker\\s+run.*(--pid=host|--network=host|--ipc=host)"),

            // ================================================================
            // 容器/命名空间逃逸
            // ================================================================
            rule("nsenter 容器逃逸", "(?i)\\bnsenter\\s+--(mount|uts|ipc|net|pid)\\s+--target"),
            rule("unshare 命名空间逃逸", "(?i)\\bunshare\\s+-(U|m|p|n|i|f)"),

            // ================================================================
            // 权限/认证相关
            // ================================================================
            rule("chmod 777 通配设置", "(?i)\\bchmod\\s+(-R\\s+)?777\\b"),
            rule("chown -R 递归修改所有者", "(?i)\\bchown\\s+-R\\s+\\w+:\\w+\\s+/"),
            rule("useradd/usermod 账户操作", "(?i)^\\s*(useradd|usermod|userdel|adduser|deluser)\\b"),
            rule("passwd 修改密码", "(?i)^\\s*passwd\\b"),
            rule("chage 密码策略修改", "(?i)^\\s*chage\\s+"),
            rule("setfacl 异常ACL设置", "(?i)\\bsetfacl\\s+-[md]\\s+[ug]:\\w+"),

            // ================================================================
            // SSH 隧道（可能用于绕过防火墙/横向移动）
            // ================================================================
            rule("SSH 动态/反向隧道", "(?i)\\bssh\\s+.*-(D|R|L)\\s+\\d+"),
            rule("SSH agent 转发", "(?i)\\bssh\\s+-A\\b"),
            rule("SSH 密钥生成(可能覆盖)", "(?i)\\bssh-keygen\\s+"),

            // ================================================================
            // 防火墙/安全配置修改
            // ================================================================
            rule("iptables 规则修改", "(?i)^\\s*iptables\\s+-[ADIFPR]"),
            rule("关闭 SELinux/AppArmor/防火墙", "(?i)(setenforce\\s+0|systemctl\\s+stop\\s+(firewalld|ufw|apparmor|iptables|nftables))"),
            rule("nftables 规则修改", "(?i)^\\s*nft\\s+(add|insert|delete|flush)"),

            // ================================================================
            // 可疑来源下载
            // ================================================================
            rule("下载可疑来源文件", "(?i)(curl|wget).*(pastebin|ngrok|burpcollaborator|transfer\\.sh|file\\.io|webhook\\.site|canarytokens|requestbin|pipeless)"),

            // ================================================================
            // 进程替换/dev/tcp (bash 高级特性)
            // ================================================================
            rule("bash /dev/tcp 反向连接", "(?i)/dev/tcp/\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}/\\d+"),

            // ================================================================
            // 隐蔽执行
            // ================================================================
            rule("nohup/disown 后台化进程", "(?i)^\\s*(nohup|disown)\\b"),
            rule("base64 解码并执行", "(?i)\\bbase64\\s+-d.*\\|\\s*(ba)?sh"),
            rule("base64 解码执行(xargs)", "(?i)\\bbase64\\s+-d\\s+<<<\\s+.*\\|.*sh"),
            rule("xxd 十六进制解码执行", "(?i)\\bxxd\\s+-[rp].*\\|\\s*(ba)?sh"),

            // ================================================================
            // 包管理器滥用
            // ================================================================
            rule("pip 可疑安装源", "(?i)\\bpip3?\\s+install\\s+.*(--index-url|--extra-index-url|--trusted-host)"),
            rule("npm 全局安装管道", "(?i)\\bnpm\\s+install\\s+-g\\s+.*\\|"),
            rule("gem 安装可疑源", "(?i)\\bgem\\s+install\\s+.*--source\\s+http"),

            // ================================================================
            // 间接命令执行
            // ================================================================
            rule("find -exec 执行命令", "(?i)\\bfind\\s+\\S+\\s+.*-(exec|execdir)\\s"),
            rule("xargs 执行命令", "(?i)\\bxargs\\s+-[iI].*(sh|bash|python)"),
            rule("eval 动态执行", "(?i)\\beval\\s+[\"\\$`]"),
            rule("source 执行临时文件", "(?i)\\b(source|\\.)\\s+(\\S*/tmp|/dev/shm|/var/tmp)"),
            rule("bash -c 执行动态命令", "(?i)\\b(bash|sh|zsh)\\s+-c\\s+[\"\\$`(]"),
            rule("命令替换混淆 $(...)", "(?i)\\$\\(.*(curl|wget|nc|bash|sh|python|chmod|rm\\s)"),
            rule("vi/vim 编辑系统关键文件", "(?i)^\\s*(vi|vim?|nano|emacs)\\s+/(etc|boot|root/\\\\.ssh)"),

            // ================================================================
            // 持久化/定时任务
            // ================================================================
            rule("at/batch 延迟任务", "(?i)^\\s*(at|batch)\\s+\\d"),
            rule("screen/tmux 会话连接", "(?i)\\b(screen|tmux)\\s+-[rxS]"),

            // ================================================================
            // 进程/调试工具滥用
            // ================================================================
            rule("strace 附加进程", "(?i)\\bstrace\\s+-p\\s+\\d+"),
            rule("gdb 附加进程", "(?i)\\bgdb\\s+-p\\s+\\d+"),

            // ================================================================
            // 数据外传/嗅探
            // ================================================================
            rule("tcpdump 抓包写入", "(?i)\\btcpdump\\s+-[iI]\\w*\\s+-w\\s"),
            rule("tar 打包敏感目录外传", "(?i)\\btar\\s+-c[zfj]+[cvf]+\\s+\\S*/(etc|var/log|home|root).*\\|.*(nc|curl|ssh)"),
            rule("DNS 隧道工具", "(?i)\\b(dns2tcp|dnscat2|iodine|dnsteal)\\b"),

            // ================================================================
            // Here-doc 滥用
            // ================================================================
            rule("heredoc 写入系统文件", "(?i)<<\\s*['\"]?(EOF|END|FIN)['\"]?\\s*.*/(etc|bin|sbin|boot|usr/bin)"),

            // ================================================================
            // 文件属性/隐藏
            // ================================================================
            rule("setfacl 修改系统ACL", "(?i)\\bsetfacl\\s+-[Rm]\\s+/(etc|bin|usr|var)"),
            rule("chattr 修改不可变属性", "(?i)\\bchattr\\s+[-+][ai]"),

            // ================================================================
            // 进程隐藏
            // ================================================================
            rule("挂载覆盖进程目录", "(?i)\\bmount\\s+--bind\\s+\\S+\\s+/proc/\\d+")
    );

    // ==================== 角色定义 ====================

    public static final String MYADMIN = "admin";
    public static final String MYOPERATOR = "ops";
    public static final String MYGUEST = "guest";

    // ==================== 在线状态 ====================

    public static final int ONLINE = 1;
    public static final int NOT_ONLINE = 0;

    // ==================== 工具方法 ====================

    /**
     * 快捷构造一条匹配规则
     *
     * @param name    规则名称（用于审计日志和前端展示）
     * @param pattern 正则表达式
     */
    private static RuleEntry rule(String name, String pattern) {
        return new RuleEntry(name, Pattern.compile(pattern));
    }

    /**
     * 一条静态匹配规则：名称 + 编译后的正则
     *
     * @param name    规则的人类可读名称
     * @param pattern 已编译的 {@link Pattern}
     */
    public record RuleEntry(String name, Pattern pattern) {

        /**
         * 测试给定命令是否命中此规则
         *
         * @param command 用户输入的完整命令
         * @return true 表示命中
         */
        public boolean matches(String command) {
            return pattern.matcher(command).find();
        }
    }
}
