package ASSRONE.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Resolves the client IP for a request, defensively, behind an optional reverse proxy.
 *
 * By default (no trusted proxy configured, or the request's direct peer is not one of
 * them) this returns {@code request.getRemoteAddr()} — X-Forwarded-For is never even
 * read. Only when the immediate TCP peer is a configured trusted proxy do we consult
 * X-Forwarded-For, and even then only a syntactically valid IP literal is ever returned.
 *
 * X-Forwarded-For is a comma-separated hop list, each proxy appending the peer it saw to
 * the right end: {@code client, proxy1, proxy2}, with proxy2 being the one that talked to
 * us. We walk it right-to-left, skipping entries that are themselves trusted proxies (our
 * own infrastructure relaying the request), and return the first entry that isn't — the
 * client as observed by our nearest trusted hop. Walking from the right (instead of just
 * taking the leftmost entry) means an attacker prepending fake entries at the start of the
 * header cannot influence the result: those are only reached last, and the walk already
 * stopped at the first genuine, non-trusted hop before getting there. A chain made up
 * entirely of trusted proxies, or any entry that fails IP-literal validation, aborts the
 * walk and falls back to remoteAddr rather than ever guessing.
 *
 * IP-literal validation is hand-written and never touches the network: it never calls
 * InetAddress.getByName/getAllByName, which for a string that merely looks IP-shaped but
 * isn't a valid literal (e.g. "1:2:3:zzzz") can fall through to a real DNS lookup. Only
 * strings this class has already confirmed are valid literals are handed to Spring
 * Security's IpAddressMatcher for the actual trusted-proxy/CIDR comparison.
 */
@Slf4j
@Component
public class ClientIpResolver {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");

    private final List<IpAddressMatcher> trustedProxies;

    public ClientIpResolver(@Value("${app.security.trusted-proxies:}") String trustedProxiesCsv) {
        this.trustedProxies = parseTrustedProxies(trustedProxiesCsv);
    }

    private static List<IpAddressMatcher> parseTrustedProxies(String csv) {
        List<IpAddressMatcher> matchers = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return matchers;
        }
        for (String entry : csv.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                matchers.add(new IpAddressMatcher(trimmed));
            } catch (IllegalArgumentException ex) {
                log.warn("Entrée app.security.trusted-proxies ignorée (format invalide) : {}", trimmed);
            }
        }
        return matchers;
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();

        if (trustedProxies.isEmpty() || !isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        String header = request.getHeader(FORWARDED_FOR_HEADER);
        if (header == null || header.isBlank()) {
            return remoteAddr;
        }

        return extractClientIp(header).orElse(remoteAddr);
    }

    private Optional<String> extractClientIp(String header) {
        String[] hops = header.split(",", -1);

        for (int i = hops.length - 1; i >= 0; i--) {
            String candidate = hops[i].trim();
            if (!isValidIpLiteral(candidate)) {
                return Optional.empty();
            }
            if (!isTrustedProxy(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private boolean isTrustedProxy(String address) {
        for (IpAddressMatcher matcher : trustedProxies) {
            try {
                if (matcher.matches(address)) {
                    return true;
                }
            } catch (IllegalArgumentException ex) {
                log.debug("Comparaison d'adresse ignorée pour {} : {}", address, ex.getMessage());
            }
        }
        return false;
    }

    private static boolean isValidIpLiteral(String candidate) {
        return !candidate.isEmpty() && (IPV4_PATTERN.matcher(candidate).matches() || isValidIpv6(candidate));
    }

    private static boolean isValidIpv6(String candidate) {
        if (candidate.length() < 2 || candidate.indexOf(':') < 0) {
            return false;
        }
        int doubleColon = candidate.indexOf("::");
        if (doubleColon >= 0 && doubleColon != candidate.lastIndexOf("::")) {
            return false;
        }

        String head = doubleColon >= 0 ? candidate.substring(0, doubleColon) : candidate;
        String tail = doubleColon >= 0 ? candidate.substring(doubleColon + 2) : "";

        String[] headGroups = head.isEmpty() ? new String[0] : head.split(":", -1);
        String[] tailGroups = tail.isEmpty() ? new String[0] : tail.split(":", -1);

        for (String group : headGroups) {
            if (!isHexGroup(group)) {
                return false;
            }
        }
        for (String group : tailGroups) {
            if (!isHexGroup(group)) {
                return false;
            }
        }

        int totalGroups = headGroups.length + tailGroups.length;
        return doubleColon >= 0 ? totalGroups <= 7 : totalGroups == 8;
    }

    private static boolean isHexGroup(String group) {
        if (group.isEmpty() || group.length() > 4) {
            return false;
        }
        for (int i = 0; i < group.length(); i++) {
            if (Character.digit(group.charAt(i), 16) == -1) {
                return false;
            }
        }
        return true;
    }
}
