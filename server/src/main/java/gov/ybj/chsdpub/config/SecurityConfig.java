package gov.ybj.chsdpub.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.ybj.chsdpub.audit.AuditService;
import gov.ybj.chsdpub.auth.CurrentUser;
import gov.ybj.chsdpub.common.GlobalExceptionHandler;
import gov.ybj.chsdpub.common.Roles;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static gov.ybj.chsdpub.common.Roles.*;

/**
 * 接口鉴权与菜单裁剪共用 Roles.PAGES：每个业务接口前缀归属一个页面。
 * 越权访问一律 403 并写入审计日志（类型「越权尝试」）。
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwt, JdbcTemplate jdbc, ObjectMapper om,
                                           AuditService audit) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(c -> {})
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/auth/config", "/api/v1/auth/login", "/api/v1/auth/sms-code",
                                "/api/v1/auth/identity", "/actuator/health", "/actuator/info", "/error").permitAll()
                        .requestMatchers("/api/v1/auth/**").authenticated()
                        // ---- 第一批 / 第三批：每个开发分组只使用自己的接口前缀
                        .requestMatchers("/api/v1/holo/**").hasAnyRole(Roles.of("A2"))
                        .requestMatchers("/api/v1/indicators/**").hasAnyRole(Roles.of("A4"))
                        .requestMatchers("/api/v1/recommend/**").hasAnyRole(Roles.of("A6"))
                        .requestMatchers("/api/v1/publish/**").hasAnyRole(Roles.of("A8"))
                        .requestMatchers("/api/v1/portal/**").hasAnyRole(INSTITUTION)
                        .requestMatchers("/api/v1/county/**").hasAnyRole(Roles.of("C1"))
                        .requestMatchers("/api/v1/province/**").hasAnyRole(Roles.of("C2"))
                        .requestMatchers("/api/v1/supervision/**").hasAnyRole(Roles.of("C3"))
                        // ---- 第二批
                        .requestMatchers("/api/v1/collection/**").hasAnyRole(Roles.of("A3"))
                        .requestMatchers("/api/v1/chart-templates/**", "/api/v1/report-blocks/**", "/api/v1/report-presets/**",
                                "/api/v1/report-drafts/**").hasAnyRole(Roles.of("A5"))
                        // 专家组列席只读：审定 / 重新生成 / 提交仅限成稿角色
                        .requestMatchers(HttpMethod.GET, "/api/v1/topics/**").hasAnyRole(Roles.of("A7"))
                        .requestMatchers("/api/v1/topics/*/submit").hasAnyRole(CONVENER, ADMIN_GROUP)
                        .requestMatchers("/api/v1/topics/**").hasAnyRole(CONVENER, ADMIN_GROUP, ANALYST)
                        .requestMatchers("/api/v1/flow-templates/**").hasAnyRole(Roles.of("A9"))
                        .requestMatchers("/api/v1/opinions/**").hasAnyRole(Roles.of("A10"))
                        .requestMatchers("/api/v1/alerts/**").hasAnyRole(Roles.of("A11"))
                        .requestMatchers("/api/v1/admin/**").hasAnyRole(Roles.of("A12"))
                        .requestMatchers("/api/v1/display-policy/**", "/api/v1/benchmark-tiers/**").hasAnyRole(Roles.of("A13"))
                        .requestMatchers("/api/v1/audit/**").hasAnyRole(Roles.of("A14"))
                        // 导出：受控环境与审计员各自导出本页结果，服务端按内容范围再校验
                        .requestMatchers("/api/v1/exports/**").hasAnyRole(Roles.of("E1"))
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                write(res, om, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "未登录或登录已过期"))
                        .accessDeniedHandler((req, res, ex) -> {
                            var u = CurrentUser.orNull();
                            if (u != null) audit.overreach(u, req.getMethod() + " " + req.getRequestURI(), AuditService.clientIp(req));
                            write(res, om, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                                    u != null && SECURITY_ADMIN.equals(u.role()) ? "安全管理员不能查看业务数据" : "当前身份无权访问该数据");
                        }))
                .addFilterBefore(new JwtAuthFilter(jwt, jdbc), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void write(HttpServletResponse res, ObjectMapper om, int status, String code, String msg) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        om.writeValue(res.getOutputStream(), GlobalExceptionHandler.body(code, msg));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(props.cors().allowedOrigins());
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        c.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", c);
        return src;
    }
}
