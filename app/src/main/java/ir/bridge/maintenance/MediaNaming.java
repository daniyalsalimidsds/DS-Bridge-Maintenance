package ir.bridge.maintenance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Pure-Java deterministic naming/identity rules for inspection and migrated media. */
public final class MediaNaming {
    private static final String FA="۰۱۲۳۴۵۶۷۸۹", AR="٠١٢٣٤٥٦٧٨٩";
    private MediaNaming() {}

    public static String compactDate(String date) {
        String s = date == null ? "" : date.replaceAll("[^0-9۰-۹٠-٩]", "");
        s = normalizeDigits(s);
        if (s.length() >= 8) return s.substring(0, 8);
        return s.isEmpty() ? "unknown-date" : s;
    }

    public static String safeName(String value) {
        String n = value == null ? "تصویر" : value.trim();
        n = n.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "-").replaceAll("\\s+", " ").trim();
        if (n.isEmpty()) n = "تصویر";
        if (n.length() > 55) n = n.substring(0, 55).trim();
        return n;
    }

    public static String stripExtension(String value) {
        return (value == null ? "" : value).replaceFirst("\\.[A-Za-z0-9]{2,5}$", "");
    }

    public static String legacyMediaId(String ownerKind, String ownerId, String logicalSlot, String binaryHash) throws Exception {
        String stableKey = nullToEmpty(ownerKind)+"\n"+nullToEmpty(ownerId)+"\n"+nullToEmpty(logicalSlot)+"\n"+nullToEmpty(binaryHash);
        return "legacy-" + sha256(stableKey.getBytes(StandardCharsets.UTF_8)).substring(0, 40);
    }

    public static String normalizeDigits(String s) {
        StringBuilder b=new StringBuilder();
        for(char c:(s==null?"":s).toCharArray()) { int i=FA.indexOf(c); if(i<0)i=AR.indexOf(c); b.append(i>=0?(char)('0'+i):c); }
        return b.toString();
    }

    public static String sha256(byte[] bytes) throws Exception {
        MessageDigest md=MessageDigest.getInstance("SHA-256");md.update(bytes);StringBuilder b=new StringBuilder();for(byte x:md.digest())b.append(String.format(Locale.ROOT,"%02x",x));return b.toString();
    }
    private static String nullToEmpty(String s){return s==null?"":s;}
}
