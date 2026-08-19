package com.zhanghan.sshproxyproject.common.utils;

import jakarta.annotation.Resource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;



@Component
@Slf4j
public class EmailUtil {

    @Resource
    private JavaMailSender mailSender;
    @Value("${spring.mail.username}")
    private String fromEmail;

    //主题标题
        private final String subject = "【SSH Bastion】";

    /**
     * 发送HTML验证码邮件
     * @param to 收件人邮箱
     * @param code 6位验证码
     */
    public void sendCodeMail(String to,String code) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);

        String htmlContent = "<div style='margin:0;padding:0;background-color:#f7f8fa;font-family:\"Microsoft YaHei\",Arial,sans-serif;'>" +
                "<div style='padding:40px 20px;'>" +
                "<div style='background:#ffffff;border-radius:16px;box-shadow:0 4px 20px rgba(0,100,200,0.08);max-width:520px;margin:0 auto;overflow:hidden;'>" +
                // 顶部渐变横幅
                "<div style='background:linear-gradient(135deg,#007bff,#0056b3);padding:24px;text-align:center;'>" +
                "<h2 style='color:#fff;margin:0;font-size:22px;letter-spacing:1px;'>账号注册验证码</h2>" +
                "</div>" +
                "<div style='padding:32px;'>" +
                "<p style='font-size:16px;color:#333333;margin:0 0 24px 0;line-height:1.7;'>您好，感谢您注册本站账号，您的专属验证码如下：</p>" +
                // 验证码高亮区块
                "<div style='background:#f0f7ff;border-radius:12px;padding:20px;text-align:center;margin-bottom:24px;'>" +
                "<p style='font-size:32px;font-weight:700;color:#0056b3;letter-spacing:8px;margin:0;'>" + code + "</p>" +
                "</div>" +
                "<hr style='border:none;border-top:1px solid #eeeeee;margin:28px 0;'>" +
                // 提示文字
                "<div style='display:flex;align-items:flex-start;gap:10px;margin-bottom:12px;'>" +
                "<span style='color:#ff9500;font-size:18px;'>⚠️</span>" +
                "<p style='font-size:14px;color:#666;margin:0;line-height:1.6;'>验证码有效期为 <strong>5分钟</strong>，超时需要重新获取。</p>" +
                "</div>" +
                "<div style='display:flex;align-items:flex-start;gap:10px;'>" +
                "<span style='color:#ff3b30;font-size:18px;'>🛡️</span>" +
                "<p style='font-size:14px;color:#666;margin:0;line-height:1.6;'>请勿将验证码告知他人</p>" +
                "</div>" +
                "</div>" +
                // 底部页脚
                "<div style='background:#f8f9fa;padding:16px;text-align:center;'>" +
                "<p style='font-size:13px;color:#999;margin:0;'>本邮件由系统自动发送，请勿直接回复</p>" +
                "<p style='font-size:12px;color:#bbbbbb;margin:8px 0 0 0;'>© 2026 zhanglife.xyz 版权所有</p>" +
                "</div>" +
                "</div>" +
                "</div>" +
                "</div>";
        helper.setText(htmlContent, true);
        mailSender.send(message);
    }
}
