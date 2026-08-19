package com.zhanghan.sshproxyproject.common.utils;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

import java.util.regex.Pattern;

/**
 * 邮箱格式校验工具类
 * <p>
 * 提供邮箱格式的合法性校验，规则参考 RFC 5322：
 * 1. 整体长度不超过 254 个字符（RFC 规定邮箱最长 254 位）
 * 2. 本地部分（@ 前）不超过 64 个字符
 * 3. 本地部分允许字母、数字及 . _ % + - 字符
 * 4. 域名部分要求至少有一个点分隔的层级，顶级域名至少 2 位字母
 * <p>
 * 使用方式：{@code boolean ok = EmailValidateUtil.isValidEmail("a@b.com");}
 */
public final class EmailValidateUtil {

    /**
     * 邮箱正则：兼容多数主流邮箱（本地部分 + @ + 域名 + 顶级域名）
     * 注意：过于严苛的正则会误伤合法邮箱，此处采用业界常用折中方案
     */
    private static final String EMAIL_REGEX =
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";

    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX);

    /** RFC 5322 规定的邮箱整体最大长度 */
    private static final int MAX_TOTAL_LENGTH = 254;

    /** RFC 5322 规定的本地部分（@ 之前）最大长度 */
    private static final int MAX_LOCAL_LENGTH = 64;

    private EmailValidateUtil() {
        // 工具类，禁止实例化
    }

    /**
     * 校验邮箱格式是否合法
     *
     * @param email 待校验的邮箱，允许为 null 或空串
     * @return true 表示格式合法；null/空串/格式错误 均返回 false
     */
    public static boolean isValidEmail(String email) {
        return validateEmail(email) == null;
    }

    /**
     * 校验邮箱格式，返回具体错误原因
     *
     * @param email 待校验的邮箱，允许为 null 或空串
     * @return 合法返回 null；否则返回中文错误提示（可直接展示给用户）
     */
    public static String validateEmail(String email) {
        // 1. 判空
        if (email == null || email.trim().isEmpty()) {
            return "邮箱不能为空";
        }

        email = email.trim();

        // 2. 长度限制
        if (email.length() > MAX_TOTAL_LENGTH) {
            return "邮箱长度不能超过 " + MAX_TOTAL_LENGTH + " 个字符";
        }

        // 3. 本地部分长度限制
        String localPart = email.substring(0, email.indexOf('@') == -1 ? email.length() : email.indexOf('@'));
        if (localPart.length() > MAX_LOCAL_LENGTH) {
            return "邮箱用户名部分（@之前）不能超过 " + MAX_LOCAL_LENGTH + " 个字符";
        }

        // 4. 正则格式校验
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return "邮箱格式不正确";
        }

        // 5. 使用 jakarta.mail 内置解析做严格兜底（依赖已存在）
        try {
            InternetAddress address = new InternetAddress(email);
            address.validate();
        } catch (AddressException e) {
            return "邮箱格式不正确";
        }

        return null;
    }
}
