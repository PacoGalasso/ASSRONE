package ASSRONE.backend.security;

/**
 * The single, explicit Content-Security-Policy applied to every backend
 * response (see SecurityConfig). Every directive here was chosen from what
 * this application actually loads, verified by building the production
 * frontend bundle and probing it against this exact policy in a real
 * browser — not assumed:
 *
 * - script-src 'self' / script-src-attr 'none': no inline script or event
 *   handler exists anywhere in the built frontend. (Angular's production
 *   build defaults to inlining a critical-CSS <style> plus an inline
 *   onload= handler on the deferred stylesheet <link> — both were disabled
 *   at the source via angular.json's optimization.styles.inlineCritical
 *   rather than relaxed here, since a browser probe confirmed both are
 *   otherwise rejected by this policy.)
 * - style-src 'self': same probe confirms no unsafe-inline is needed once
 *   critical-CSS inlining is disabled.
 * - img-src 'self' blob:: committee photos and the profile avatar are both
 *   served from this origin; the avatar is additionally re-displayed via a
 *   blob: object URL (see ProfileService/profile.ts).
 * - font-src 'self' https://fonts.gstatic.com: the Google Fonts CSS
 *   (@import in styles.css) is resolved and inlined into the build output
 *   by the Angular CLI at build time — fonts.googleapis.com is never
 *   contacted at runtime — but the resulting @font-face rules still point
 *   at fonts.gstatic.com for the actual .woff2 files.
 * - connect-src 'self': every HTTP call in the frontend uses a relative
 *   /api or /auth path; there is no absolute cross-origin API URL anywhere.
 * - frame-src/frame-ancestors 'none', object-src 'none': no iframe, plugin,
 *   or embed exists anywhere in the app, and nothing embeds this site.
 * - base-uri/form-action 'self': no dynamic &lt;base&gt; and no form posts to
 *   another origin exist anywhere in the app.
 *
 * upgrade-insecure-requests is appended only in production (see
 * app.security.csp.upgrade-insecure-requests) — it would break the plain
 * HTTP Angular dev server if it were always on.
 */
public final class ContentSecurityPolicy {

    private static final String BASE_DIRECTIVES = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "script-src-attr 'none'",
            "style-src 'self'",
            "img-src 'self' blob:",
            "font-src 'self' https://fonts.gstatic.com",
            "connect-src 'self'",
            "frame-src 'none'",
            "frame-ancestors 'none'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'"
    );

    private ContentSecurityPolicy() {
    }

    public static String directives(boolean upgradeInsecureRequests) {
        return upgradeInsecureRequests ? BASE_DIRECTIVES + "; upgrade-insecure-requests" : BASE_DIRECTIVES;
    }
}
