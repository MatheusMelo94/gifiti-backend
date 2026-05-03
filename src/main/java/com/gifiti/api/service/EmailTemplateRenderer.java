package com.gifiti.api.service;

import com.gifiti.api.model.enums.Language;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.Locale;

/**
 * Renders the HTML body and subject for transactional emails using
 * locale-aware copy from the project {@link MessageSource}.
 *
 * <p>Replaces the static-utility {@code EmailTemplates} class. Two contracts
 * differ from the prior design:</p>
 * <ol>
 *   <li><strong>Locale comes from the {@link Language} method argument.</strong>
 *       The renderer never reads {@code LocaleContextHolder} so emails survive
 *       {@code @Async}, {@code @Scheduled}, or future job-queue offloading
 *       that loses request-thread context (plan § Risk #2).</li>
 *   <li><strong>Every runtime argument is HTML-escaped via
 *       {@link HtmlUtils#htmlEscape(String)} before substitution.</strong>
 *       This is the F-6 amendment contract
 *       ({@code specs/005-i18n-backend-support/plan-amendment-f6.md}) — applied
 *       even to server-generated URLs ("renderer escapes all runtime args,"
 *       not "renderer escapes user-controlled args"). Defense in depth.
 *       Bundle template strings themselves are developer-controlled trusted
 *       content and are NOT re-escaped.</li>
 * </ol>
 *
 * <p>Convention citations: {@code architecture-conventions.md § Package Layout}
 * (services own orchestration), {@code § Backend (Spring Boot) Conventions}
 * (constructor-injected {@code @Component}, no field injection).</p>
 */
@Component
public class EmailTemplateRenderer {

    private final MessageSource messageSource;

    public EmailTemplateRenderer(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * Renders the email-verification message in the given language.
     *
     * <p><strong>F-6 contract (mandatory):</strong></p>
     * <pre>
     * All runtime arguments passed by callers (e.g., URLs, identifiers, future
     * user-controlled values such as display name) are HTML-escaped via
     * org.springframework.web.util.HtmlUtils.htmlEscape before being substituted
     * into the rendered HTML body.
     *
     * CALLERS MUST NOT pre-escape arguments — passing already-escaped HTML would
     * result in double-escaping (e.g., "&amp;" becoming "&amp;amp;amp;"). Pass
     * plain values; the renderer owns escaping.
     *
     * The message-bundle template strings themselves (chrome HTML, copy strings)
     * are developer-controlled and are NOT re-escaped — they are trusted content
     * authored by the development team.
     * </pre>
     *
     * @param language email locale (must not be {@code null}); drives bundle
     *                 selection, NEVER read from {@code LocaleContextHolder}.
     * @param verifyUrl absolute URL the user clicks to verify their email; pass
     *                  the plain URL — escaping is the renderer's responsibility.
     * @return rendered email content (subject + HTML body).
     */
    public RenderedEmail verification(Language language, String verifyUrl) {
        Locale locale = language.toLocale();
        String escapedUrl = HtmlUtils.htmlEscape(verifyUrl);

        String subject = messageSource.getMessage("email.verification.subject", null, locale);
        String welcome = messageSource.getMessage("email.verification.welcome", null, locale);
        String body = messageSource.getMessage("email.verification.body", null, locale);
        String cta = messageSource.getMessage("email.verification.cta", null, locale);
        String fallbackNotice = messageSource.getMessage(
                "email.verification.fallback.notice", null, locale);
        String fallbackLinkText = messageSource.getMessage(
                "email.verification.fallback.link", new Object[]{escapedUrl}, locale);
        String ignoreNotice = messageSource.getMessage(
                "email.verification.ignore.notice", null, locale);
        String footerCopyright = messageSource.getMessage(
                "email.verification.footer.copyright", null, locale);
        String footerSignup = messageSource.getMessage(
                "email.verification.footer.signup", null, locale);

        String htmlBody = """
            <!DOCTYPE html>
            <html lang="%s">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>%s</title>
            </head>
            <body style="margin: 0; padding: 0; background-color: #09090b; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;">
              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color: #09090b; padding: 40px 16px;">
                <tr>
                  <td align="center">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width: 520px; background-color: #18181b; border-radius: 16px; overflow: hidden;">
                      <!-- Header with logo -->
                      <tr>
                        <td align="center" style="padding: 40px 40px 24px 40px;">
                          <img src="https://www.ggifiti.com/gifiti-logo.png" alt="Gifiti" width="200" style="display: block; height: auto;" />
                        </td>
                      </tr>
                      <!-- Heading: Welcome to + logo -->
                      <tr>
                        <td align="center" style="padding: 0 40px 8px 40px;">
                          <h1 style="margin: 0; font-size: 24px; font-weight: 700; color: #fafafa; line-height: 1.3;">
                            %s
                            <img src="https://www.ggifiti.com/gifiti-logo.png" alt="Gifiti" height="28" style="vertical-align: middle; height: 28px; width: auto;" />
                          </h1>
                        </td>
                      </tr>
                      <!-- Body text -->
                      <tr>
                        <td align="center" style="padding: 0 40px 32px 40px;">
                          <p style="margin: 0; font-size: 15px; line-height: 1.6; color: #a1a1aa;">
                            %s
                          </p>
                        </td>
                      </tr>
                      <!-- CTA Button -->
                      <tr>
                        <td align="center" style="padding: 0 40px 32px 40px;">
                          <table role="presentation" cellpadding="0" cellspacing="0">
                            <tr>
                              <td align="center" style="border-radius: 10px; background: linear-gradient(135deg, #60a5fa, #1d4ed8);">
                                <a href="%s" target="_blank" style="display: inline-block; padding: 14px 40px; font-size: 15px; font-weight: 600; color: #ffffff; text-decoration: none; border-radius: 10px;">
                                  %s
                                </a>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                      <!-- Divider -->
                      <tr>
                        <td style="padding: 0 40px;">
                          <div style="height: 1px; background-color: #27272a;"></div>
                        </td>
                      </tr>
                      <!-- Fallback link -->
                      <tr>
                        <td align="center" style="padding: 24px 40px 8px 40px;">
                          <p style="margin: 0; font-size: 12px; color: #71717a; line-height: 1.5;">
                            %s
                          </p>
                        </td>
                      </tr>
                      <tr>
                        <td align="center" style="padding: 0 40px 24px 40px; word-break: break-all;">
                          <a href="%s" style="font-size: 12px; color: #60a5fa; text-decoration: underline; line-height: 1.5;">
                            %s
                          </a>
                        </td>
                      </tr>
                      <!-- Ignore notice -->
                      <tr>
                        <td align="center" style="padding: 0 40px 40px 40px;">
                          <p style="margin: 0; font-size: 12px; color: #52525b; line-height: 1.5;">
                            %s
                          </p>
                        </td>
                      </tr>
                    </table>
                    <!-- Footer -->
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width: 520px;">
                      <tr>
                        <td align="center" style="padding: 24px 40px 0 40px;">
                          <p style="margin: 0; font-size: 11px; color: #3f3f46; line-height: 1.5;">
                            %s
                          </p>
                        </td>
                      </tr>
                      <tr>
                        <td align="center" style="padding: 4px 40px 40px 40px;">
                          <p style="margin: 0; font-size: 11px; color: #3f3f46; line-height: 1.5;">
                            %s
                            <a href="https://www.ggifiti.com" style="color: #60a5fa; text-decoration: none;">ggifiti.com</a>
                          </p>
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """.formatted(
                language.getTag(),
                subject,
                welcome,
                body,
                escapedUrl,
                cta,
                fallbackNotice,
                escapedUrl,
                fallbackLinkText,
                ignoreNotice,
                footerCopyright,
                footerSignup);

        return new RenderedEmail(subject, htmlBody);
    }

    /**
     * Renders the password-reset message in the given language.
     *
     * <p><strong>F-6 contract (mandatory):</strong></p>
     * <pre>
     * All runtime arguments passed by callers (e.g., URLs, identifiers, future
     * user-controlled values such as display name) are HTML-escaped via
     * org.springframework.web.util.HtmlUtils.htmlEscape before being substituted
     * into the rendered HTML body.
     *
     * CALLERS MUST NOT pre-escape arguments — passing already-escaped HTML would
     * result in double-escaping (e.g., "&amp;" becoming "&amp;amp;amp;"). Pass
     * plain values; the renderer owns escaping.
     *
     * The message-bundle template strings themselves (chrome HTML, copy strings)
     * are developer-controlled and are NOT re-escaped — they are trusted content
     * authored by the development team.
     * </pre>
     *
     * @param language email locale (must not be {@code null}); drives bundle
     *                 selection, NEVER read from {@code LocaleContextHolder}.
     * @param resetUrl absolute URL the user clicks to set a new password; pass
     *                 the plain URL — escaping is the renderer's responsibility.
     * @return rendered email content (subject + HTML body).
     */
    public RenderedEmail passwordReset(Language language, String resetUrl) {
        Locale locale = language.toLocale();
        String escapedUrl = HtmlUtils.htmlEscape(resetUrl);

        String subject = messageSource.getMessage("email.password.reset.subject", null, locale);
        String heading = messageSource.getMessage("email.password.reset.heading", null, locale);
        String body = messageSource.getMessage("email.password.reset.body", null, locale);
        String cta = messageSource.getMessage("email.password.reset.cta", null, locale);
        String fallbackNotice = messageSource.getMessage(
                "email.password.reset.fallback.notice", null, locale);
        String fallbackLinkText = messageSource.getMessage(
                "email.password.reset.fallback.link", new Object[]{escapedUrl}, locale);
        String ignoreNotice = messageSource.getMessage(
                "email.password.reset.ignore.notice", null, locale);
        String footerCopyright = messageSource.getMessage(
                "email.password.reset.footer.copyright", null, locale);
        String footerNotice = messageSource.getMessage(
                "email.password.reset.footer.notice", null, locale);

        String htmlBody = """
            <!DOCTYPE html>
            <html lang="%s">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>%s</title>
            </head>
            <body style="margin: 0; padding: 0; background-color: #09090b; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;">
              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color: #09090b; padding: 40px 16px;">
                <tr>
                  <td align="center">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width: 520px; background-color: #18181b; border-radius: 16px; overflow: hidden;">
                      <!-- Header with logo -->
                      <tr>
                        <td align="center" style="padding: 40px 40px 24px 40px;">
                          <img src="https://www.ggifiti.com/gifiti-logo.png" alt="Gifiti" width="200" style="display: block; height: auto;" />
                        </td>
                      </tr>
                      <!-- Heading -->
                      <tr>
                        <td align="center" style="padding: 0 40px 8px 40px;">
                          <h1 style="margin: 0; font-size: 24px; font-weight: 700; color: #fafafa; line-height: 1.3;">
                            %s
                          </h1>
                        </td>
                      </tr>
                      <!-- Body text -->
                      <tr>
                        <td align="center" style="padding: 0 40px 32px 40px;">
                          <p style="margin: 0; font-size: 15px; line-height: 1.6; color: #a1a1aa;">
                            %s
                          </p>
                        </td>
                      </tr>
                      <!-- CTA Button -->
                      <tr>
                        <td align="center" style="padding: 0 40px 32px 40px;">
                          <table role="presentation" cellpadding="0" cellspacing="0">
                            <tr>
                              <td align="center" style="border-radius: 10px; background: linear-gradient(135deg, #60a5fa, #1d4ed8);">
                                <a href="%s" target="_blank" style="display: inline-block; padding: 14px 40px; font-size: 15px; font-weight: 600; color: #ffffff; text-decoration: none; border-radius: 10px;">
                                  %s
                                </a>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                      <!-- Divider -->
                      <tr>
                        <td style="padding: 0 40px;">
                          <div style="height: 1px; background-color: #27272a;"></div>
                        </td>
                      </tr>
                      <!-- Fallback link -->
                      <tr>
                        <td align="center" style="padding: 24px 40px 8px 40px;">
                          <p style="margin: 0; font-size: 12px; color: #71717a; line-height: 1.5;">
                            %s
                          </p>
                        </td>
                      </tr>
                      <tr>
                        <td align="center" style="padding: 0 40px 24px 40px; word-break: break-all;">
                          <a href="%s" style="font-size: 12px; color: #60a5fa; text-decoration: underline; line-height: 1.5;">
                            %s
                          </a>
                        </td>
                      </tr>
                      <!-- Ignore notice -->
                      <tr>
                        <td align="center" style="padding: 0 40px 40px 40px;">
                          <p style="margin: 0; font-size: 12px; color: #52525b; line-height: 1.5;">
                            %s
                          </p>
                        </td>
                      </tr>
                    </table>
                    <!-- Footer -->
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width: 520px;">
                      <tr>
                        <td align="center" style="padding: 24px 40px 0 40px;">
                          <p style="margin: 0; font-size: 11px; color: #3f3f46; line-height: 1.5;">
                            %s
                          </p>
                        </td>
                      </tr>
                      <tr>
                        <td align="center" style="padding: 4px 40px 40px 40px;">
                          <p style="margin: 0; font-size: 11px; color: #3f3f46; line-height: 1.5;">
                            %s
                            <a href="https://www.ggifiti.com" style="color: #60a5fa; text-decoration: none;">ggifiti.com</a>
                          </p>
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """.formatted(
                language.getTag(),
                subject,
                heading,
                body,
                escapedUrl,
                cta,
                fallbackNotice,
                escapedUrl,
                fallbackLinkText,
                ignoreNotice,
                footerCopyright,
                footerNotice);

        return new RenderedEmail(subject, htmlBody);
    }

    /**
     * Rendered email payload returned by the renderer methods.
     *
     * @param subject  the resolved Subject header (locale-specific).
     * @param htmlBody the fully-rendered HTML body (chrome + locale-specific copy
     *                 + HTML-escaped runtime arguments).
     */
    public record RenderedEmail(String subject, String htmlBody) {}
}
