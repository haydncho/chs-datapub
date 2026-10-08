package cn.ybdata.core.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/** Payload validation for the A3–A7 actions: every refusal is a 400 with a Chinese message. */
final class Checks {

    /** names: CJK, letters, digits, space and a few joining marks — no markup, quotes or formula prefixes */
    static final Pattern NAME = Pattern.compile("^[\\p{IsHan}A-Za-z0-9][\\p{IsHan}A-Za-z0-9 ()\\uFF08\\uFF09·._/%+\\-、,\\uFF0C]*$");
    /** formula / condition text: names plus comparison and arithmetic operators */
    static final Pattern EXPR = Pattern.compile("^[\\p{IsHan}A-Za-z0-9Σ][\\p{IsHan}A-Za-z0-9Σ ()\\uFF08\\uFF09·._/%+\\-×÷*=≠<>≥≤、,\\uFF0C]*$");

    private Checks() {}

    /** required trimmed text, 1..max chars, matching {@code pattern} */
    static String text(JsonNode p, String field, String label, int max, Pattern pattern) {
        JsonNode v = p == null ? null : p.get(field);
        String s = v == null || v.isNull() ? "" : v.isTextual() ? v.asText().trim() : null;
        if (s == null) throw new IllegalArgumentException(label + "格式不正确");
        if (s.isEmpty()) throw new IllegalArgumentException("请填写" + label);
        if (s.codePointCount(0, s.length()) > max) throw new IllegalArgumentException(label + "不能超过 " + max + " 个字");
        if (pattern != null && !pattern.matcher(s).matches()) throw new IllegalArgumentException(label + "含有不允许的特殊字符");
        return s;
    }

    static String optionalText(JsonNode p, String field, String label, int max, Pattern pattern) {
        JsonNode v = p == null ? null : p.get(field);
        if (v == null || v.isNull() || (v.isTextual() && v.asText().isBlank())) return "";
        return text(p, field, label, max, pattern);
    }

    /** required value from a fixed set */
    static String oneOf(JsonNode p, String field, String label, Collection<String> allowed) {
        JsonNode v = p == null ? null : p.get(field);
        String s = v == null || !v.isTextual() ? null : v.asText();
        if (s == null || !allowed.contains(s)) throw new IllegalArgumentException(label + "不正确,可选:" + String.join(" / ", allowed));
        return s;
    }

    /** required integer in [min, max] */
    static int index(JsonNode p, String field, String label, int min, int max) {
        JsonNode v = p == null ? null : p.get(field);
        if (v == null || !v.isIntegralNumber() || v.asInt() < min || v.asInt() > max) {
            throw new IllegalArgumentException(label + "不正确");
        }
        return v.asInt();
    }

    static boolean bool(JsonNode p, String field) {
        JsonNode v = p == null ? null : p.get(field);
        if (v == null || v.isNull()) return false;
        if (!v.isBoolean()) throw new IllegalArgumentException(field + " 须为 true / false");
        return v.asBoolean();
    }

    /** array of short texts (each validated), at most {@code maxItems} */
    static List<String> texts(JsonNode p, String field, String label, int maxItems, int maxLen, Pattern pattern) {
        JsonNode v = p == null ? null : p.get(field);
        List<String> out = new ArrayList<>();
        if (v == null || v.isNull()) return out;
        if (!v.isArray()) throw new IllegalArgumentException(label + "格式不正确");
        if (v.size() > maxItems) throw new IllegalArgumentException(label + "最多 " + maxItems + " 项");
        for (JsonNode it : v) {
            String s = it.isTextual() ? it.asText().trim() : "";
            if (s.isEmpty()) throw new IllegalArgumentException(label + "不能为空");
            if (s.codePointCount(0, s.length()) > maxLen) throw new IllegalArgumentException(label + "每项不能超过 " + maxLen + " 个字");
            if (pattern != null && !pattern.matcher(s).matches()) throw new IllegalArgumentException(label + "「" + s + "」含有不允许的特殊字符");
            if (out.contains(s)) throw new IllegalArgumentException(label + "「" + s + "」重复");
            out.add(s);
        }
        return out;
    }
}
