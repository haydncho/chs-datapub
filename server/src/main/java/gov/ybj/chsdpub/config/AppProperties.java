package gov.ybj.chsdpub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Engine engine, Jwt jwt, Cors cors, Demo demo, Security security, String zone) {

    public record Engine(String url, int connectTimeoutMs, int readTimeoutMs) {}

    public record Jwt(String secret, int ttlHours) {}

    public record Cors(List<String> allowedOrigins) {}

    /** 演示模式：任意密码、固定短信验证码、模拟识别 UKey（caHolder 为证书持有人账号）。生产环境必须关闭。 */
    public record Demo(boolean enabled, String smsCode, String caHolder) {}

    public record Security(int loginMaxFailures, int loginLockMinutes) {}
}
