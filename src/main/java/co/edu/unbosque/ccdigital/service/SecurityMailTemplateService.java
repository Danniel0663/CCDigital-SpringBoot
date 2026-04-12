package co.edu.unbosque.ccdigital.service;

import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Construye plantillas HTML y texto plano para correos transaccionales de seguridad.
 */
@Service
public class SecurityMailTemplateService {

    /**
     * Contenido listo para envío.
     *
     * @param subject asunto
     * @param textBody versión texto plano
     * @param htmlBody versión HTML
     */
    public record MailContent(String subject, String textBody, String htmlBody) {}

    public MailContent registrationVerificationCode(String displayName, String code, long ttlMinutes) {
        return buildCodeEmail(
                "CCDigital - Verificación de correo para registro",
                "Registro de cuenta",
                "Confirma tu correo para crear la cuenta",
                "Usa este código para verificar tu correo y completar el registro en CCDigital.",
                displayName,
                code,
                ttlMinutes,
                "Si no iniciaste este registro, puedes ignorar este mensaje."
        );
    }

    public MailContent loginVerificationCode(String displayName, String code, long ttlMinutes) {
        return buildCodeEmail(
                "CCDigital - Código de verificación de ingreso",
                "Ingreso seguro",
                "Confirma que eres tú quien está entrando",
                "Usa este código para terminar el ingreso a tu cuenta en CCDigital.",
                displayName,
                code,
                ttlMinutes,
                "Si no intentaste iniciar sesión, cambia tu contraseña y revisa tu cuenta."
        );
    }

    public MailContent passwordResetCode(String displayName, String code, long ttlMinutes) {
        return buildCodeEmail(
                "CCDigital - Código de recuperación de contraseña",
                "Recuperación de cuenta",
                "Restablece tu contraseña con este código",
                "Usa este código temporal para continuar el cambio de contraseña en CCDigital.",
                displayName,
                code,
                ttlMinutes,
                "Si no pediste cambiar la contraseña, ignora este correo."
        );
    }

    public MailContent suspiciousLoginAlert(String displayName) {
        String safeName = safeName(displayName);
        String subject = "CCDigital - Alerta de seguridad en tu cuenta";
        String textBody = """
                Hola %s,

                Detectamos varios intentos fallidos al validar el segundo factor de tu cuenta CCDigital.

                Si no fuiste tú:
                - cambia tu contraseña cuanto antes;
                - revisa si reconoces la actividad reciente;
                - contacta al equipo administrador de la plataforma.

                Este correo fue generado automáticamente por CCDigital.
                """.formatted(safeName);

        String htmlBody = """
                <!doctype html>
                <html lang="es">
                <body style="margin:0;padding:0;background:#f6f7fb;font-family:'Segoe UI',Arial,sans-serif;color:#101828;">
                  <div style="display:none;max-height:0;overflow:hidden;opacity:0;">
                    Alerta de seguridad sobre intentos fallidos de ingreso en tu cuenta CCDigital.
                  </div>
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f6f7fb;padding:24px 0;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:620px;width:100%%;">
                          <tr>
                            <td style="padding:0 20px;">
                              <div style="height:4px;background:linear-gradient(90deg,#2f6fed,#0ea5e9,#0f9d58);border-radius:999px 999px 0 0;"></div>
                              <div style="background:#0f172a;color:#ffffff;padding:22px 28px;border-radius:22px 22px 0 0;">
                                <div style="font-size:12px;letter-spacing:.12em;text-transform:uppercase;opacity:.78;">CCDigital</div>
                                <div style="font-size:28px;line-height:1.15;font-weight:800;margin-top:8px;">Alerta de seguridad</div>
                              </div>
                              <div style="background:#ffffff;border:1px solid rgba(15,23,42,.08);border-top:none;border-radius:0 0 22px 22px;box-shadow:0 14px 34px rgba(16,24,40,.08);padding:28px;">
                                <div style="display:inline-block;padding:7px 12px;border-radius:999px;background:#fef3f2;color:#b42318;font-size:12px;font-weight:700;letter-spacing:.04em;text-transform:uppercase;">Seguridad de cuenta</div>
                                <h1 style="margin:18px 0 10px;font-size:28px;line-height:1.15;color:#101828;">Hola %s</h1>
                                <p style="margin:0 0 16px;font-size:16px;line-height:1.7;color:#475467;">
                                  Detectamos varios intentos fallidos al validar el segundo factor de tu cuenta CCDigital.
                                </p>
                                <div style="background:#f8fbff;border:1px solid rgba(47,111,237,.14);border-radius:18px;padding:18px 20px;margin:18px 0;">
                                  <div style="font-size:14px;font-weight:700;color:#101828;margin-bottom:10px;">Qué te recomendamos ahora</div>
                                  <ul style="margin:0;padding-left:18px;color:#475467;font-size:14px;line-height:1.7;">
                                    <li>Cambiar tu contraseña si no reconoces la actividad.</li>
                                    <li>Revisar si el intento de ingreso fue realizado por ti.</li>
                                    <li>Contactar al equipo administrador si necesitas apoyo.</li>
                                  </ul>
                                </div>
                                <p style="margin:0;font-size:13px;line-height:1.7;color:#667085;">
                                  Este mensaje fue generado automáticamente por CCDigital para ayudarte a proteger tu identidad digital.
                                </p>
                              </div>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(escapeHtml(safeName));

        return new MailContent(subject, textBody, htmlBody);
    }

    private MailContent buildCodeEmail(String subject,
                                       String eyebrow,
                                       String heading,
                                       String intro,
                                       String displayName,
                                       String code,
                                       long ttlMinutes,
                                       String closingAdvice) {
        String safeName = safeName(displayName);
        String safeCode = normalizeCode(code);
        long safeTtl = Math.max(1, ttlMinutes);

        String textBody = """
                Hola %s,

                %s

                Codigo: %s

                Este codigo vence en %d minutos y solo puede usarse una vez.
                No lo compartas con nadie.

                %s

                CCDigital
                Universidad El Bosque
                """.formatted(safeName, intro, safeCode, safeTtl, closingAdvice);

        String htmlBody = """
                <!doctype html>
                <html lang="es">
                <body style="margin:0;padding:0;background:#f6f7fb;font-family:'Segoe UI',Arial,sans-serif;color:#101828;">
                  <div style="display:none;max-height:0;overflow:hidden;opacity:0;">
                    %s
                  </div>
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f6f7fb;padding:24px 0;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:620px;width:100%%;">
                          <tr>
                            <td style="padding:0 20px;">
                              <div style="height:4px;background:linear-gradient(90deg,#2f6fed,#0ea5e9,#0f9d58);border-radius:999px 999px 0 0;"></div>
                              <div style="background:#0f172a;color:#ffffff;padding:22px 28px;border-radius:22px 22px 0 0;">
                                <div style="font-size:12px;letter-spacing:.12em;text-transform:uppercase;opacity:.78;">CCDigital</div>
                                <div style="font-size:28px;line-height:1.15;font-weight:800;margin-top:8px;">Tu codigo de seguridad</div>
                                <div style="margin-top:8px;font-size:14px;line-height:1.6;opacity:.82;">Identidad digital y gestion documental ciudadana</div>
                              </div>
                              <div style="background:#ffffff;border:1px solid rgba(15,23,42,.08);border-top:none;border-radius:0 0 22px 22px;box-shadow:0 14px 34px rgba(16,24,40,.08);padding:28px;">
                                <div style="display:inline-block;padding:7px 12px;border-radius:999px;background:#eef4ff;color:#2f6fed;font-size:12px;font-weight:700;letter-spacing:.04em;text-transform:uppercase;">%s</div>
                                <h1 style="margin:18px 0 10px;font-size:28px;line-height:1.15;color:#101828;">%s</h1>
                                <p style="margin:0 0 8px;font-size:16px;line-height:1.7;color:#475467;">Hola %s,</p>
                                <p style="margin:0 0 18px;font-size:16px;line-height:1.7;color:#475467;">%s</p>

                                <div style="margin:24px 0;padding:22px;border-radius:20px;background:linear-gradient(180deg,#f8fbff 0%%,#eef4ff 100%%);border:1px solid rgba(47,111,237,.16);text-align:center;">
                                  <div style="font-size:12px;text-transform:uppercase;letter-spacing:.12em;color:#2f6fed;font-weight:800;">Codigo unico</div>
                                  <div style="margin-top:12px;font-size:36px;line-height:1.1;font-weight:800;letter-spacing:.18em;color:#101828;">%s</div>
                                  <div style="margin-top:14px;font-size:13px;color:#667085;">Vigente por %d minutos. Solo puede usarse una vez.</div>
                                </div>

                                <div style="background:#f8fbff;border:1px solid rgba(15,23,42,.08);border-radius:18px;padding:18px 20px;margin:20px 0 18px;">
                                  <div style="font-size:14px;font-weight:700;color:#101828;margin-bottom:10px;">Recomendaciones</div>
                                  <ul style="margin:0;padding-left:18px;color:#475467;font-size:14px;line-height:1.7;">
                                    <li>No compartas este codigo con terceros.</li>
                                    <li>Si el tiempo expira, solicita uno nuevo desde la plataforma.</li>
                                    <li>Verifica siempre que estas en CCDigital antes de ingresarlo.</li>
                                  </ul>
                                </div>

                                <p style="margin:0 0 14px;font-size:14px;line-height:1.7;color:#475467;">%s</p>
                                <p style="margin:0;font-size:13px;line-height:1.7;color:#667085;">
                                  Este correo fue enviado automaticamente por CCDigital. Si necesitas ayuda, comunicate con el equipo administrador de la plataforma.
                                </p>
                              </div>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                escapeHtml(heading),
                escapeHtml(eyebrow),
                escapeHtml(heading),
                escapeHtml(safeName),
                escapeHtml(intro),
                escapeHtml(safeCode),
                safeTtl,
                escapeHtml(closingAdvice)
        );

        return new MailContent(subject, textBody, htmlBody);
    }

    private String safeName(String displayName) {
        String normalized = displayName == null ? "" : displayName.trim();
        return normalized.isBlank() ? "usuario" : normalized;
    }

    private String normalizeCode(String code) {
        String normalized = code == null ? "" : code.trim();
        return normalized.isBlank() ? "------" : normalized;
    }

    private String escapeHtml(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }
}
