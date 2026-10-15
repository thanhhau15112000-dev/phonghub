package com.phonghub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Ngưỡng giới hạn tần suất (#77); mỗi rule là "tối đa N yêu cầu trong cửa sổ W giây" cho một khóa. */
@ConfigurationProperties(prefix = "phonghub.rate-limit")
public class RateLimitProperties {

    public static class Rule {
        private int maxRequests;
        private int windowSeconds;

        public Rule() {
        }

        public Rule(int maxRequests, int windowSeconds) {
            this.maxRequests = maxRequests;
            this.windowSeconds = windowSeconds;
        }

        public int getMaxRequests() {
            return maxRequests;
        }

        public void setMaxRequests(int maxRequests) {
            this.maxRequests = maxRequests;
        }

        public int getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }

    private boolean enabled = true;

    /**
     * Chỉ bật khi app chạy sau reverse proxy tin cậy (Render...). Khi bật, IP khách lấy từ X-Forwarded-For;
     * khi tắt luôn dùng địa chỉ kết nối trực tiếp để client không giả mạo header.
     */
    private boolean trustForwardedFor;

    /** Số proxy tin cậy nằm trước app; IP khách là phần tử thứ N tính từ bên phải của X-Forwarded-For. */
    private int trustedProxyHops = 1;

    private Rule login = new Rule(10, 60);
    private Rule loginAccount = new Rule(10, 300);
    private Rule forgotPassword = new Rule(5, 300);
    private Rule forgotPasswordTarget = new Rule(3, 900);
    /** Webhook SePay được nới rộng để các lần retry hợp lệ không bị chặn nhầm. */
    private Rule webhook = new Rule(300, 60);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isTrustForwardedFor() {
        return trustForwardedFor;
    }

    public void setTrustForwardedFor(boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    public int getTrustedProxyHops() {
        return trustedProxyHops;
    }

    public void setTrustedProxyHops(int trustedProxyHops) {
        this.trustedProxyHops = trustedProxyHops;
    }

    public Rule getLogin() {
        return login;
    }

    public void setLogin(Rule login) {
        this.login = login;
    }

    public Rule getLoginAccount() {
        return loginAccount;
    }

    public void setLoginAccount(Rule loginAccount) {
        this.loginAccount = loginAccount;
    }

    public Rule getForgotPassword() {
        return forgotPassword;
    }

    public void setForgotPassword(Rule forgotPassword) {
        this.forgotPassword = forgotPassword;
    }

    public Rule getForgotPasswordTarget() {
        return forgotPasswordTarget;
    }

    public void setForgotPasswordTarget(Rule forgotPasswordTarget) {
        this.forgotPasswordTarget = forgotPasswordTarget;
    }

    public Rule getWebhook() {
        return webhook;
    }

    public void setWebhook(Rule webhook) {
        this.webhook = webhook;
    }
}
