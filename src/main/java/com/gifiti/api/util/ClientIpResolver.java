package com.gifiti.api.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Shared utility for resolving the request's client IP with the same
 * trusted-proxy logic used by {@link com.gifiti.api.config.RateLimitFilter}
 * (feature 008 / T6 — extracted per Security findings F-2 pin 4).
 *
 * <p>Why extracted: the access-code rate-limit consumption point lives in the
 * service layer (ADR 0008 § Decision G architectural note), but it must read
 * the SAME client IP the filter would have read — otherwise an attacker can
 * trivially bypass the per-(IP, shareableId) bucket by spoofing
 * {@code X-Forwarded-For} from a request that arrives outside the trusted
 * proxy chain. This class centralizes the resolution so the filter and the
 * service share one canonical implementation.
 *
 * <p>Per architecture-conventions § Stack Baseline: utility classes are
 * static-only with no public constructor.
 *
 * <p>Per Security findings F-7 (pre-existing convention drift): the
 * {@link #isTrustedProxy} predicate is hardcoded to RFC 1918 + localhost
 * ranges, matching the existing {@code RateLimitFilter} behavior. Promoting
 * this to a configurable {@code TRUSTED_PROXIES} property is out of scope for
 * feature 008.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
        throw new UnsupportedOperationException("ClientIpResolver is a static utility");
    }

    /**
     * Resolve the request's client IP with {@code X-Forwarded-For} spoofing
     * protection.
     *
     * <p>Security: only trusts {@code X-Forwarded-For} when the request
     * arrived from a known proxy (see {@link #isTrustedProxy}). Uses the
     * rightmost non-trusted IP — the last client before the proxy chain — so
     * an attacker prepending fake values cannot influence the resolved IP.
     *
     * @param request the inbound HTTP request
     * @return the client IP — never {@code null}
     */
    public static String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();

        // Only trust X-Forwarded-For if request came from trusted proxy.
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String[] ips = xForwardedFor.split(",");
            // Use rightmost non-trusted IP (last client before our proxy chain).
            for (int i = ips.length - 1; i >= 0; i--) {
                String ip = ips[i].trim();
                if (!isTrustedProxy(ip)) {
                    return ip;
                }
            }
        }
        return remoteAddr;
    }

    /**
     * Check if {@code ip} is in a known trusted-proxy range.
     *
     * <p>Hardcoded to RFC 1918 private ranges + localhost — matches existing
     * {@code RateLimitFilter.isTrustedProxy} behavior. Security findings F-7
     * tracks the convention-drift fix; out of scope for feature 008.
     */
    public static boolean isTrustedProxy(String ip) {
        if (ip == null) return false;
        return ip.startsWith("10.") ||
               ip.startsWith("172.16.") || ip.startsWith("172.17.") ||
               ip.startsWith("172.18.") || ip.startsWith("172.19.") ||
               ip.startsWith("172.20.") || ip.startsWith("172.21.") ||
               ip.startsWith("172.22.") || ip.startsWith("172.23.") ||
               ip.startsWith("172.24.") || ip.startsWith("172.25.") ||
               ip.startsWith("172.26.") || ip.startsWith("172.27.") ||
               ip.startsWith("172.28.") || ip.startsWith("172.29.") ||
               ip.startsWith("172.30.") || ip.startsWith("172.31.") ||
               ip.startsWith("192.168.") ||
               ip.equals("127.0.0.1") ||
               ip.equals("0:0:0:0:0:0:0:1");
    }

    /**
     * Mask the last IPv4 octet for logging (privacy). IPv6 and malformed IPs
     * are returned unchanged — they do not contain an IPv4-style trailing
     * identifier to mask. {@code null} is normalized to {@code "unknown"}.
     */
    public static String maskIp(String ip) {
        if (ip == null) return "unknown";
        if (ip.length() < 4) return "***";
        int lastDot = ip.lastIndexOf('.');
        if (lastDot < 0) {
            // IPv6 or otherwise non-IPv4 — leave as-is.
            return ip;
        }
        return ip.substring(0, lastDot) + ".xxx";
    }
}
