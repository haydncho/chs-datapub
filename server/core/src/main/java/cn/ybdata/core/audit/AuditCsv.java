package cn.ybdata.core.audit;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** CSV rendering for GET /audit/export.csv: UTF-8 with BOM (opens correctly in Excel), RFC 4180 quoting. */
final class AuditCsv {

    static final String BOM = "\uFEFF";
    static final List<String> HEADER = List.of("编号", "时间", "类型", "操作人", "角色", "页面", "动作", "对象", "IP", "终端",
            "水印编号", "链式校验", "非工作时间", "变更前", "变更后", "前序哈希", "哈希", "导出水印");

    private AuditCsv() {}

    static byte[] render(List<AuditService.AuditView> rows, String exportWatermark) {
        StringBuilder sb = new StringBuilder(BOM);
        line(sb, HEADER);
        for (AuditService.AuditView v : rows) {
            line(sb, List.of(String.valueOf(v.id()),
                    v.at().atZoneSameInstant(AuditTypes.ZONE).toLocalDateTime().withNano(0).toString().replace('T', ' '),
                    v.type(), v.who(), v.role(), v.page() + " " + AuditService.pageTitle(v.page()), v.action(), v.object(),
                    v.ip(), v.terminal(), v.watermark(), "ok".equals(v.chain()) ? "通过" : "失败",
                    v.offHours() ? "是" : "否", v.diff() == null ? "" : v.diff().from(), v.diff() == null ? "" : v.diff().to(),
                    v.prevHash(), v.hash(), exportWatermark));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void line(StringBuilder sb, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(cell(cells.get(i)));
        }
        sb.append("\r\n");
    }

    /** Quote when needed; neutralise spreadsheet formulas (=, +, -, @) so an exported value never executes. */
    static String cell(String s) {
        if (s == null) return "";
        String v = s;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
