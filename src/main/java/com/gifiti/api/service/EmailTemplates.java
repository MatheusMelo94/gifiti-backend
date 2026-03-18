package com.gifiti.api.service;

public final class EmailTemplates {

    private EmailTemplates() {}

    public static String verification(String verifyUrl) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>Confirm your email - Gifiti</title>
            </head>
            <body style="margin: 0; padding: 0; background-color: #09090b; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;">
              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color: #09090b; padding: 40px 16px;">
                <tr>
                  <td align="center">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width: 520px; background-color: #18181b; border-radius: 16px; overflow: hidden;">
                      <tr>
                        <td align="center" style="padding: 40px 40px 24px 40px;">
                          <img src="https://www.ggifiti.com/gifiti-logo.png" alt="Gifiti" width="200" style="display: block; height: auto;" />
                        </td>
                      </tr>
                      <tr>
                        <td align="center" style="padding: 0 40px 8px 40px;">
                          <h1 style="margin: 0; font-size: 24px; font-weight: 700; color: #fafafa; line-height: 1.3;">
                            Welcome to Gifiti!
                          </h1>
                        </td>
                      </tr>
                      <tr>
                        <td align="center" style="padding: 0 40px 32px 40px;">
                          <p style="margin: 0; font-size: 15px; line-height: 1.6; color: #a1a1aa;">
                            Thanks for signing up. Please confirm your email address to start creating and sharing wishlists with friends and family.
                          </p>
                        </td>
                      </tr>
                      <tr>
                        <td align="center" style="padding: 0 40px 32px 40px;">
                          <table role="presentation" cellpadding="0" cellspacing="0">
                            <tr>
                              <td align="center" style="border-radius: 10px; background: linear-gradient(135deg, #60a5fa, #1d4ed8);">
                                <a href="%s" target="_blank" style="display: inline-block; padding: 14px 40px; font-size: 15px; font-weight: 600; color: #ffffff; text-decoration: none; border-radius: 10px;">
                                  Confirm Email Address
                                </a>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                      <tr>
                        <td style="padding: 0 40px;">
                          <div style="height: 1px; background-color: #27272a;"></div>
                        </td>
                      </tr>
                      <tr>
                        <td align="center" style="padding: 24px 40px 8px 40px;">
                          <p style="margin: 0; font-size: 12px; color: #71717a; line-height: 1.5;">
                            Button not working? Copy and paste this link into your browser:
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
                      <tr>
                        <td align="center" style="padding: 0 40px 16px 40px;">
                          <table role="presentation" cellpadding="0" cellspacing="0" style="background-color: #27272a; border-radius: 12px; overflow: hidden;">
                            <tr>
                              <td style="padding: 16px 24px; text-align: center;">
                                <p style="margin: 0 0 8px 0; font-size: 11px; font-weight: 600; color: #71717a; text-transform: uppercase; letter-spacing: 1px;">
                                  Link expires in
                                </p>
                                <table role="presentation" cellpadding="0" cellspacing="0" style="margin: 0 auto;">
                                  <tr>
                                    <td align="center" style="padding: 0 8px;">
                                      <div style="width: 52px; height: 52px; line-height: 52px; font-size: 24px; font-weight: 700; color: #fafafa; background-color: #09090b; border-radius: 10px; text-align: center;">
                                        24
                                      </div>
                                      <p style="margin: 6px 0 0 0; font-size: 10px; color: #71717a; text-transform: uppercase; letter-spacing: 0.5px;">
                                        Hours
                                      </p>
                                    </td>
                                    <td align="center" style="padding: 0 2px; vertical-align: top;">
                                      <div style="font-size: 22px; font-weight: 700; color: #52525b; line-height: 52px;">:</div>
                                    </td>
                                    <td align="center" style="padding: 0 8px;">
                                      <div style="width: 52px; height: 52px; line-height: 52px; font-size: 24px; font-weight: 700; color: #fafafa; background-color: #09090b; border-radius: 10px; text-align: center;">
                                        00
                                      </div>
                                      <p style="margin: 6px 0 0 0; font-size: 10px; color: #71717a; text-transform: uppercase; letter-spacing: 0.5px;">
                                        Min
                                      </p>
                                    </td>
                                    <td align="center" style="padding: 0 2px; vertical-align: top;">
                                      <div style="font-size: 22px; font-weight: 700; color: #52525b; line-height: 52px;">:</div>
                                    </td>
                                    <td align="center" style="padding: 0 8px;">
                                      <div style="width: 52px; height: 52px; line-height: 52px; font-size: 24px; font-weight: 700; color: #fafafa; background-color: #09090b; border-radius: 10px; text-align: center;">
                                        00
                                      </div>
                                      <p style="margin: 6px 0 0 0; font-size: 10px; color: #71717a; text-transform: uppercase; letter-spacing: 0.5px;">
                                        Sec
                                      </p>
                                    </td>
                                  </tr>
                                </table>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                      <tr>
                        <td align="center" style="padding: 0 40px 40px 40px;">
                          <p style="margin: 0; font-size: 12px; color: #52525b; line-height: 1.5;">
                            If you didn't create a Gifiti account, you can safely ignore this email.
                          </p>
                        </td>
                      </tr>
                    </table>
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width: 520px;">
                      <tr>
                        <td align="center" style="padding: 24px 40px 0 40px;">
                          <p style="margin: 0; font-size: 11px; color: #3f3f46; line-height: 1.5;">
                            &copy; 2026 Gifiti. All rights reserved.
                          </p>
                        </td>
                      </tr>
                      <tr>
                        <td align="center" style="padding: 4px 40px 40px 40px;">
                          <p style="margin: 0; font-size: 11px; color: #3f3f46; line-height: 1.5;">
                            You received this email because you signed up at
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
            """.formatted(verifyUrl, verifyUrl, verifyUrl);
    }

    public static String passwordReset(String resetUrl) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>Reset your password - Gifiti</title>
            </head>
            <body style="margin: 0; padding: 0; background-color: #09090b; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;">
              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color: #09090b; padding: 40px 16px;">
                <tr>
                  <td align="center">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width: 520px; background-color: #18181b; border-radius: 16px; overflow: hidden;">
                      <tr>
                        <td align="center" style="padding: 40px 40px 24px 40px;">
                          <img src="https://www.ggifiti.com/gifiti-logo.png" alt="Gifiti" width="200" style="display: block; height: auto;" />
                        </td>
                      </tr>
                      <tr>
                        <td align="center" style="padding: 0 40px 8px 40px;">
                          <h1 style="margin: 0; font-size: 24px; font-weight: 700; color: #fafafa; line-height: 1.3;">
                            Reset your password
                          </h1>
                        </td>
                      </tr>
                      <tr>
                        <td align="center" style="padding: 0 40px 32px 40px;">
                          <p style="margin: 0; font-size: 15px; line-height: 1.6; color: #a1a1aa;">
                            We received a request to reset your Gifiti password. Click the button below to choose a new one.
                          </p>
                        </td>
                      </tr>
                      <tr>
                        <td align="center" style="padding: 0 40px 32px 40px;">
                          <table role="presentation" cellpadding="0" cellspacing="0">
                            <tr>
                              <td align="center" style="border-radius: 10px; background: linear-gradient(135deg, #60a5fa, #1d4ed8);">
                                <a href="%s" target="_blank" style="display: inline-block; padding: 14px 40px; font-size: 15px; font-weight: 600; color: #ffffff; text-decoration: none; border-radius: 10px;">
                                  Reset Password
                                </a>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                      <tr>
                        <td style="padding: 0 40px;">
                          <div style="height: 1px; background-color: #27272a;"></div>
                        </td>
                      </tr>
                      <tr>
                        <td align="center" style="padding: 24px 40px 8px 40px;">
                          <p style="margin: 0; font-size: 12px; color: #71717a; line-height: 1.5;">
                            Button not working? Copy and paste this link into your browser:
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
                      <tr>
                        <td align="center" style="padding: 0 40px 16px 40px;">
                          <table role="presentation" cellpadding="0" cellspacing="0" style="background-color: #27272a; border-radius: 12px; overflow: hidden;">
                            <tr>
                              <td style="padding: 16px 24px; text-align: center;">
                                <p style="margin: 0 0 8px 0; font-size: 11px; font-weight: 600; color: #71717a; text-transform: uppercase; letter-spacing: 1px;">
                                  Link expires in
                                </p>
                                <table role="presentation" cellpadding="0" cellspacing="0" style="margin: 0 auto;">
                                  <tr>
                                    <td align="center" style="padding: 0 8px;">
                                      <div style="width: 52px; height: 52px; line-height: 52px; font-size: 24px; font-weight: 700; color: #fafafa; background-color: #09090b; border-radius: 10px; text-align: center;">
                                        01
                                      </div>
                                      <p style="margin: 6px 0 0 0; font-size: 10px; color: #71717a; text-transform: uppercase; letter-spacing: 0.5px;">
                                        Hours
                                      </p>
                                    </td>
                                    <td align="center" style="padding: 0 2px; vertical-align: top;">
                                      <div style="font-size: 22px; font-weight: 700; color: #52525b; line-height: 52px;">:</div>
                                    </td>
                                    <td align="center" style="padding: 0 8px;">
                                      <div style="width: 52px; height: 52px; line-height: 52px; font-size: 24px; font-weight: 700; color: #fafafa; background-color: #09090b; border-radius: 10px; text-align: center;">
                                        00
                                      </div>
                                      <p style="margin: 6px 0 0 0; font-size: 10px; color: #71717a; text-transform: uppercase; letter-spacing: 0.5px;">
                                        Min
                                      </p>
                                    </td>
                                    <td align="center" style="padding: 0 2px; vertical-align: top;">
                                      <div style="font-size: 22px; font-weight: 700; color: #52525b; line-height: 52px;">:</div>
                                    </td>
                                    <td align="center" style="padding: 0 8px;">
                                      <div style="width: 52px; height: 52px; line-height: 52px; font-size: 24px; font-weight: 700; color: #fafafa; background-color: #09090b; border-radius: 10px; text-align: center;">
                                        00
                                      </div>
                                      <p style="margin: 6px 0 0 0; font-size: 10px; color: #71717a; text-transform: uppercase; letter-spacing: 0.5px;">
                                        Sec
                                      </p>
                                    </td>
                                  </tr>
                                </table>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                      <tr>
                        <td align="center" style="padding: 0 40px 40px 40px;">
                          <p style="margin: 0; font-size: 12px; color: #52525b; line-height: 1.5;">
                            If you didn't request a password reset, you can safely ignore this email. Your password will remain unchanged.
                          </p>
                        </td>
                      </tr>
                    </table>
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width: 520px;">
                      <tr>
                        <td align="center" style="padding: 24px 40px 0 40px;">
                          <p style="margin: 0; font-size: 11px; color: #3f3f46; line-height: 1.5;">
                            &copy; 2026 Gifiti. All rights reserved.
                          </p>
                        </td>
                      </tr>
                      <tr>
                        <td align="center" style="padding: 4px 40px 40px 40px;">
                          <p style="margin: 0; font-size: 11px; color: #3f3f46; line-height: 1.5;">
                            You received this email because you requested a password reset at
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
            """.formatted(resetUrl, resetUrl, resetUrl);
    }
}
