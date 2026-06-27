package com.zhanghan.sshproxyproject.dto;


import lombok.Builder;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

@Data
@Builder
public class LoginFormDTO {

    private String username;

    private String password;

}
