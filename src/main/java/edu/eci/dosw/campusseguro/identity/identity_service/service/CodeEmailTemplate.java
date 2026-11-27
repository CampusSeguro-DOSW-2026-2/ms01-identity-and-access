package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.util.List;

import org.springframework.web.util.HtmlUtils;

/**
 * HTML of the MS01 emails with a code, with the same presentation as the rest of the CampusSeguro emails: the logo and
 * the name in the header, a heading, the highlighted code and a footer. It uses tables and inline styles because email
 * clients ignore style sheets. The logo travels inside the message (Content-ID {@link #LOGO_CID}), so it is shown
 * without depending on an external address
 */
final class CodeEmailTemplate {

    /** Content-ID of the logo that is attached inline to the message */
    static final String LOGO_CID = "campusseguro-logo";

    /** Logo in the classpath, the same image of the application */
    static final String LOGO_PATH = "mail/campusseguro-logo.png";

    private static final String BRAND = "CampusSeguro";
    private static final String RED = "#990000";
    private static final String INK = "#0F172A";
    private static final String MUTED = "#64748B";
    private static final String LINE = "#E2E8F0";
    private static final String PAGE_BG = "#F8FAFC";
    private static final String CREAM = "#FFF7ED";
    private static final String FONT = "-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";

    private CodeEmailTemplate() {
    }

    /**
     * Builds the HTML of an email with a code
     *
     * @param eyebrow short text above the heading
     * @param heading heading of the message
     * @param intro first paragraph
     * @param codeLabel text that names the code
     * @param code code in clear text
     * @param notes paragraphs after the code
     * @return the HTML of the message
     */
    static String html(String eyebrow, String heading, String intro, String codeLabel, String code, List<String> notes) {
        StringBuilder html = new StringBuilder(4096);
        html.append("<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>").append(BRAND).append("</title></head>")
                .append("<body style=\"margin:0;padding:0;background:").append(PAGE_BG).append(";\">")
                .append("<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;\">")
                .append(escape(codeLabel + ": " + code)).append("</div>")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"")
                .append(" style=\"background:").append(PAGE_BG).append(";padding:24px 12px;\"><tr><td align=\"center\">")
                .append("<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"")
                .append(" style=\"width:100%;max-width:600px;background:#ffffff;border:1px solid ").append(LINE)
                .append(";border-radius:14px;\">")
                .append("<tr><td style=\"padding:22px 28px;border-bottom:3px solid ").append(RED).append(";\">")
                .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>")
                .append("<td style=\"vertical-align:middle;\"><img src=\"cid:").append(LOGO_CID)
                .append("\" width=\"44\" height=\"44\" alt=\"\" style=\"display:block;border:0;width:44px;height:44px;\"></td>")
                .append("<td style=\"vertical-align:middle;padding-left:12px;font-family:").append(FONT)
                .append(";font-size:18px;font-weight:700;color:").append(RED).append(";letter-spacing:.2px;\">")
                .append(BRAND).append("</td></tr></table></td></tr>")
                .append("<tr><td style=\"padding:28px;\">")
                .append("<p style=\"margin:0 0 10px;font-family:").append(FONT)
                .append(";font-size:12px;letter-spacing:1.2px;text-transform:uppercase;color:").append(RED)
                .append(";font-weight:700;\">").append(escape(eyebrow)).append("</p>")
                .append("<h1 style=\"margin:0 0 18px;font-family:").append(FONT)
                .append(";font-size:24px;line-height:1.3;color:").append(INK).append(";font-weight:700;\">")
                .append(escape(heading)).append("</h1>")
                .append(paragraph(intro, INK, "15px"))
                .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"")
                .append(" style=\"margin:20px 0;background:").append(CREAM).append(";border-left:4px solid ").append(RED)
                .append(";border-radius:10px;\"><tr><td style=\"padding:16px 22px;\">")
                .append("<p style=\"margin:0 0 6px;font-family:").append(FONT)
                .append(";font-size:11px;letter-spacing:1px;text-transform:uppercase;font-weight:700;color:").append(RED)
                .append(";\">").append(escape(codeLabel)).append("</p>")
                .append("<p style=\"margin:0;font-family:'SFMono-Regular',Consolas,'Liberation Mono',monospace;")
                .append("font-size:30px;font-weight:700;letter-spacing:8px;color:").append(INK).append(";\">")
                .append(escape(code)).append("</p></td></tr></table>");
        for (String note : notes) {
            html.append(paragraph(note, MUTED, "14px"));
        }
        html.append("</td></tr>")
                .append("<tr><td style=\"padding:18px 28px;background:").append(PAGE_BG)
                .append(";border-top:1px solid ").append(LINE).append(";\">")
                .append("<p style=\"margin:0;font-family:").append(FONT)
                .append(";font-size:12px;line-height:1.6;color:").append(MUTED).append(";\">")
                .append("Mensaje automático de ").append(BRAND).append(". No responda a este mensaje.</p></td></tr>")
                .append("</table></td></tr></table></body></html>");
        return html.toString();
    }

    private static String paragraph(String text, String color, String size) {
        return "<p style=\"margin:0 0 12px;font-family:" + FONT + ";font-size:" + size + ";line-height:1.6;color:"
                + color + ";\">" + escape(text) + "</p>";
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value, "UTF-8");
    }
}
