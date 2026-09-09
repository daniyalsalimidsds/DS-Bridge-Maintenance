package ir.bridge.maintenance;

/** Pure-Java path validation used by backup/media code and JVM regression tests. */
public final class PathSecurity {
    private PathSecurity() {}

    public static String sanitizeRelative(String value) {
        String s = value == null ? "" : value.replace('\\', '/');
        if (s.isEmpty() || s.startsWith("/") || s.matches("^[A-Za-z]:.*") || s.contains("../") || s.equals("..") || s.indexOf('\0') >= 0) {
            throw new SecurityException("Unsafe media path");
        }
        return s;
    }

    public static String sanitizeZipEntry(String value) {
        String s = sanitizeRelative(value);
        if (s.startsWith("./")) s = s.substring(2);
        if (s.isEmpty()) throw new SecurityException("Unsafe ZIP entry");
        return s;
    }

    public static String safeFileName(String value) {
        String n = (value == null ? "image.jpg" : value).replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").trim();
        if (n.isEmpty()) n = "image.jpg";
        return n.length() > 120 ? n.substring(n.length() - 120) : n;
    }
}
