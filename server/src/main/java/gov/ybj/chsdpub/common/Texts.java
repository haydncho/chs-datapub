package gov.ybj.chsdpub.common;

public final class Texts {
    private Texts() {}

    public static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    public static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
