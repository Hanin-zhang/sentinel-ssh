package com.zhanghan.sshproxyproject.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackendServerVO {
    //主键，表示服务器id
    @TableId(type = IdType.AUTO)
    private Integer id;

    //服务器名称
    private String serverName;

    private String host;

    //在线状态
    private Boolean status;

    //当前连接数
    private Integer connectionCount;
}
