package gov.ybj.chsdpub.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** 会话令牌校验：签名有效、账号启用、令牌版本一致（退出登录 / 停用后旧令牌即时失效）。 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final JdbcTemplate jdbc;

    public JwtAuthFilter(JwtService jwt, JdbcTemplate jdbc) {
        this.jwt = jwt;
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            jwt.parseSession(h.substring(7).trim()).filter(this::valid).ifPresent(s -> {
                var auth = new UsernamePasswordAuthenticationToken(s.user(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + s.user().role())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }
        chain.doFilter(req, res);
    }

    private boolean valid(JwtService.Session s) {
        List<Boolean> ok = jdbc.query("select enabled and token_version = ? from app_user where id = ?",
                (rs, i) -> rs.getBoolean(1), s.tokenVersion(), s.user().userId());
        return !ok.isEmpty() && ok.get(0);
    }
}
