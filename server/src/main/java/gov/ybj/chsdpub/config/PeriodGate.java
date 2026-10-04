package gov.ybj.chsdpub.config;

import gov.ybj.chsdpub.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 发布门禁:机构门户的分析数据、县区视图、省级汇总只在「最新数据期次处于已发布状态」时可读。
 * 期次被撤回(A8 撤回审批通过)后,上述接口立即返回 409,恢复发布后自动放行。
 * 分析监测区内部接口(全息图、指标等)不受影响;报告中心 / 意见 / 政策等本就以已发布版本为数据源。
 */
@Configuration
public class PeriodGate implements WebMvcConfigurer {

    private final JdbcTemplate jdbc;

    public PeriodGate(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
                if (!"GET".equals(req.getMethod())) return true;
                List<String> row = jdbc.query("select period, status from pub_period order by period desc limit 1",
                        (rs, i) -> rs.getString(1) + "|" + rs.getString(2));
                if (row.isEmpty() || row.get(0).endsWith("|PUBLISHED")) return true;
                String period = row.get(0).split("\\|")[0];
                throw ApiException.conflict(period.replace("-0", "-").replace("-", "年") + "月数据已撤回,暂停对外展示,恢复发布后自动开放");
            }
        }).addPathPatterns("/api/v1/portal/holo/**", "/api/v1/portal/groups/**", "/api/v1/portal/benchmark/**",
                "/api/v1/portal/offsite", "/api/v1/county/**", "/api/v1/province/**");
    }
}
