package cn.ybdata.core.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Minimal Java mirror of the analytics rule engine ({@code server/analytics/app/alerts.py}) for one
 * (rule, institution): used to accept a 提醒函 only for an alert the engine really raises
 * ({@code AL-{rule}-{org}}), never for a client-invented id or institution.
 */
final class AlertRules {

    record Rule(String id, String metric, String label, String kind, Double threshold, String level,
                String fmt, int decimals, int minPeers) {}

    /** a triggered alert: the shown value and the trigger condition */
    record Hit(Rule rule, String orgId, String orgName, String value, String condition) {}

    private AlertRules() {}

    static Optional<Rule> rule(JdbcClient jdbc, String id) {
        return jdbc.sql("""
                select id, metric, label, kind, threshold, level, fmt, decimals, min_peers
                from alert_rule where id = :id and enabled""").param("id", id)
                .query((rs, i) -> new Rule(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getObject(5) == null ? null : rs.getDouble(5), rs.getString(6), rs.getString(7),
                        rs.getInt(8), rs.getInt(9)))
                .optional();
    }

    /** evaluate {@code AL-{rule}-{org}} for the latest period in the data; empty = not triggered / unknown */
    static Optional<Hit> evaluate(JdbcClient jdbc, String ruleId, String orgId) {
        Optional<Rule> r = rule(jdbc, ruleId);
        if (r.isEmpty()) return Optional.empty();
        Rule rule = r.get();
        Optional<String> orgName = jdbc.sql("select name from org where id = :id and id like 'H%'")
                .param("id", orgId).query(String.class).optional();
        if (orgName.isEmpty()) return Optional.empty();
        LocalDate period = jdbc.sql("select max(period) from indicator_series").query(LocalDate.class).optional().orElse(null);
        if (period == null) return Optional.empty();
        Map<String, Double> current = values(jdbc, rule.metric(), period);
        Double v = current.get(orgId);
        if (v == null) return Optional.empty();
        String unit = "pct".equals(rule.fmt()) ? "%" : "";
        switch (rule.kind()) {
            case "mom_pct" -> {
                Double prev = values(jdbc, rule.metric(), period.minusMonths(1)).get(orgId);
                Double t = rule.threshold();
                if (prev == null || prev == 0 || t == null) return Optional.empty();
                double pct = (v - prev) / Math.abs(prev) * 100;
                if (!(t >= 0 ? pct > t : pct < t)) return Optional.empty();
                return Optional.of(new Hit(rule, orgId, orgName.get(), String.format("%+.1f%%", pct),
                        (t >= 0 ? "环比 > " : "环比 < ") + trim(t) + "%"));
            }
            case "gt_p90" -> {
                List<Double> peers = new ArrayList<>();
                current.forEach((o, x) -> {
                    if (!o.equals(orgId)) peers.add(x);
                });
                if (peers.size() < Math.max(rule.minPeers(), 1)) return Optional.empty();
                double p90 = round(percentile(peers, 90), rule.decimals());
                if (!(v > p90)) return Optional.empty();
                return Optional.of(new Hit(rule, orgId, orgName.get(), fmt(v, rule.decimals()) + unit,
                        "> P90 (" + fmt(p90, rule.decimals()) + unit + ")"));
            }
            case "gt", "lt" -> {
                Double t = rule.threshold();
                if (t == null) return Optional.empty();
                boolean hit = "gt".equals(rule.kind()) ? v > t : v < t;
                if (!hit) return Optional.empty();
                String op = "gt".equals(rule.kind()) ? "> " : "< ";
                return Optional.of(new Hit(rule, orgId, orgName.get(), fmt(v, rule.decimals()) + unit,
                        op + trim(t) + ("yuan".equals(rule.fmt()) ? " 元" : unit)));
            }
            default -> {
                return Optional.empty();
            }
        }
    }

    private static Map<String, Double> values(JdbcClient jdbc, String metric, LocalDate period) {
        return jdbc.sql("select org_id, value from indicator_series where metric = :m and period = :p")
                .param("m", metric).param("p", period)
                .query((rs, i) -> Map.entry(rs.getString(1), rs.getDouble(2))).list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** linear-interpolated percentile (numpy default), as in the analytics service */
    static double percentile(List<Double> values, double p) {
        List<Double> xs = values.stream().sorted().toList();
        double k = (xs.size() - 1) * p / 100;
        int lo = (int) k;
        int hi = Math.min(lo + 1, xs.size() - 1);
        return xs.get(lo) + (xs.get(hi) - xs.get(lo)) * (k - lo);
    }

    private static double round(double v, int decimals) {
        double f = Math.pow(10, decimals);
        return Math.round(v * f) / f;
    }

    private static String fmt(double v, int decimals) {
        return String.format("%,." + Math.max(0, decimals) + "f", v);
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
