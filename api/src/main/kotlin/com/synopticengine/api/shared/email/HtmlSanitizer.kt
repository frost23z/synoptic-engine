package com.synopticengine.api.shared.email

import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

/**
 * Sanitizes HTML against Jsoup's [Safelist.relaxed] policy extended with
 * explicit safe-link protocol enforcement.
 *
 * Apply this to every HTML body before persisting (template save) and before
 * sending (automation actions + marketing campaigns) to prevent stored XSS when
 * email content is later rendered in a browser (webmail, mail client HTML
 * preview pane, CRM timeline, etc.).
 */
object HtmlSanitizer {
    private val SAFE_LIST: Safelist =
        Safelist
            .relaxed()
            // Restrict link and image protocols to safe schemes only.
            // javascript:, data:, vbscript:, etc. are rejected by omission.
            .addProtocols("a", "href", "https", "http", "mailto")
            .addProtocols("img", "src", "https", "http", "data")

    /** Sanitize and return a safe HTML string. Input may be null/blank — returned as-is. */
    fun sanitize(html: String): String {
        if (html.isBlank()) return html
        return Jsoup.clean(html, SAFE_LIST)
    }
}
