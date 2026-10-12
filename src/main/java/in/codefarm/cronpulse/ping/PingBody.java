package in.codefarm.cronpulse.ping;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;

/**
 * The job output sent in a ping's request body, capped at {@link #MAX_BYTES}.
 * <p>
 * The cap is enforced while <em>reading</em>: we read at most {@code MAX_BYTES + 1} bytes and never
 * look at the rest. A job that pipes a 2 GB log file into curl costs us 10 KB of memory, not 2 GB.
 *
 * @param text      the stored output, or null when the request had no body
 * @param truncated true when the job sent more than we kept
 */
public record PingBody(String text, boolean truncated) {

    /** Matches the {@code octet_length(body) <= 10240} constraint on the pings table. */
    public static final int MAX_BYTES = 10 * 1024;

    static final PingBody EMPTY = new PingBody(null, false);

    public static PingBody read(InputStream in) throws IOException {
        return read(in, MAX_BYTES);
    }

    static PingBody read(InputStream in, int maxBytes) throws IOException {
        // One byte more than the limit tells us whether anything was cut off.
        byte[] bytes = in.readNBytes(maxBytes + 1);
        boolean truncated = bytes.length > maxBytes;
        int length = truncated ? startOfCharacterAt(bytes, maxBytes) : bytes.length;
        if (length == 0 && !truncated) {
            return EMPTY;
        }
        // Postgres text can't hold NUL characters, and job output is sometimes binary.
        // Invalid UTF-8 becomes U+FFFD, which is 3 bytes long, so the text can grow
        // past the limit again: trim it to fit.
        String text = new String(bytes, 0, length, UTF_8).replace("\u0000", "");
        String fitted = fitInBytes(text, maxBytes);
        return new PingBody(fitted, truncated || fitted.length() < text.length());
    }

    /**
     * Cutting a byte array at an arbitrary index can split a multi-byte UTF-8 character in half.
     * UTF-8 continuation bytes look like {@code 10xxxxxx}; step back over them to find where the
     * character at {@code index} begins, and cut there.
     */
    private static int startOfCharacterAt(byte[] bytes, int index) {
        int i = index;
        while (i > 0 && (bytes[i] & 0xC0) == 0x80) {
            i--;
        }
        return i;
    }

    private static String fitInBytes(String text, int maxBytes) {
        int bytes = 0;
        int end = 0;
        while (end < text.length()) {
            int codePoint = text.codePointAt(end);
            int size = utf8Length(codePoint);
            if (bytes + size > maxBytes) {
                break;
            }
            bytes += size;
            end += Character.charCount(codePoint);
        }
        return text.substring(0, end);
    }

    private static int utf8Length(int codePoint) {
        if (codePoint < 0x80) {
            return 1;
        }
        if (codePoint < 0x800) {
            return 2;
        }
        return codePoint < 0x10000 ? 3 : 4;
    }
}
