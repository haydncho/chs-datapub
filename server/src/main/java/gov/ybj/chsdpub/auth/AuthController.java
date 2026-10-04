package gov.ybj.chsdpub.auth;

import gov.ybj.chsdpub.config.AppProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;
    private final AppProperties props;

    public AuthController(AuthService auth, AppProperties props) {
        this.auth = auth;
        this.props = props;
    }

    /** 登录页配置：已识别证书、是否演示模式（演示模式给出账号与验证码提示）。 */
    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cert", auth.recognizedCert());
        m.put("demo", props.demo().enabled());
        if (props.demo().enabled()) {
            m.put("demoSmsCode", props.demo().smsCode());
            m.put("demoAccounts", new String[]{"chenzhiyuan", "lihua", "zhouting", "zhangyue", "zhaoqiang", "suntao", "limin", "qianli", "wanglei", "liudaibiao"});
        }
        return m;
    }

    public record SmsReq(@NotBlank(message = "请输入账号") String username) {}

    @PostMapping("/sms-code")
    public Map<String, Object> sms(@RequestBody @jakarta.validation.Valid SmsReq req) {
        return auth.sendSms(req.username().trim());
    }

    public record LoginReq(@NotBlank(message = "请选择认证方式") String method, String username, String password,
                           String smsCode, String pin) {}

    @PostMapping("/login")
    public AuthService.LoginResult login(@RequestBody @jakarta.validation.Valid LoginReq req) {
        return auth.login(req.method(), req.username(), req.password(), req.smsCode(), req.pin());
    }

    public record IdentityReq(@NotBlank(message = "登录票据缺失") String ticket, @NotNull(message = "请选择身份") Long identityId) {}

    @PostMapping("/identity")
    public AuthService.SessionResult identity(@RequestBody @jakarta.validation.Valid IdentityReq req) {
        return auth.selectIdentity(req.ticket(), req.identityId());
    }

    @GetMapping("/me")
    public AuthService.Me me() {
        return auth.me(CurrentUser.get());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        auth.logout(CurrentUser.get());
        return ResponseEntity.noContent().build();
    }
}
