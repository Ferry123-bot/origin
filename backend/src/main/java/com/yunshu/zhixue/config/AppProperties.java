package com.yunshu.zhixue.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String defaultPassword;
    private final Jwt jwt = new Jwt();
    private final Minio minio = new Minio();
    private final Sms sms = new Sms();
    private final Ai ai = new Ai();

    @Getter
    @Setter
    public static class Jwt {
        private String portalSecret;
        private String adminSecret;
        private long accessTokenMinutes = 120;
        private long refreshTokenDays = 14;
    }

    @Getter
    @Setter
    public static class Minio {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucket;
    }

    @Getter
    @Setter
    public static class Sms {
        /** 仅开发环境打开：响应里带回验证码，便于本地联调。 */
        private boolean exposeCode;
    }

    @Getter
    @Setter
    public static class Ai {
        private String baseUrl = "";
        private String model = "";
        private String apiKey = "";
    }
}
