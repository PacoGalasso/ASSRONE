package ASSRONE.backend.audit;

/**
 * Neutralizes log-forging characters and enforces a length ceiling on any
 * value that ultimately comes from a request (a header, a path segment, an
 * email, a free-text field) before it can reach a log line. Without this, a
 * value containing {@code \n}/{@code \r} could inject a fake, well-formed
 * second log line — indistinguishable from a real one to anything parsing
 * the log later (including a future SIEM).
 */
final class LogSanitizer {

    private static final int MAX_LENGTH = 200;
    private static final String TRUNCATION_SUFFIX = "...";

    private LogSanitizer() {
    }

    /**
     * Replaces every control character (CR, LF, TAB, and the rest of the
     * C0/C1 ranges) with a single space, then truncates. A run of several
     * control characters collapses to several spaces rather than one, which
     * is deliberate: it keeps the transformation a simple, fast, fixed
     * per-character mapping instead of a stateful collapse that could itself
     * become a source of subtle bugs, and readability of the resulting log
     * line is not materially different either way.
     */
    static String sanitize(String value) {
        if (value == null) {
            return "-";
        }
        StringBuilder cleaned = new StringBuilder(Math.min(value.length(), MAX_LENGTH));
        for (int i = 0; i < value.length() && cleaned.length() < MAX_LENGTH; i++) {
            char c = value.charAt(i);
            cleaned.append(isControlOrSeparator(c) ? ' ' : c);
        }
        if (value.length() > MAX_LENGTH) {
            cleaned.setLength(Math.max(0, MAX_LENGTH - TRUNCATION_SUFFIX.length()));
            cleaned.append(TRUNCATION_SUFFIX);
        }
        String result = cleaned.toString().trim();
        return result.isEmpty() ? "-" : result;
    }

    private static boolean isControlOrSeparator(char c) {
        return Character.isISOControl(c);
    }
}
