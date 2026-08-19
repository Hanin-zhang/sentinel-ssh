package com.zhanghan.sshproxyproject.vo;

import com.zhanghan.sshproxyproject.dto.Result;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CodeLimitResult {

    //是否允许发送
    private boolean allowed;

    //错误留言
    private String errorMsg;

    //过多长时间后重试
    private long retryAfterSeconds;

    public static CodeLimitResult deny(String errorMsg,long retryAfterSeconds){
        return new CodeLimitResult(false,errorMsg,retryAfterSeconds);
    }

    public static CodeLimitResult deny(String errorMsg){
        return new CodeLimitResult(false,errorMsg,0);
    }

    public static CodeLimitResult allow(){
        return new CodeLimitResult(true,null,0);
    }
}
