package com.maticcm.openwebuiclient

import java.net.URI

/**
 * Decides whether a top-level navigation stays in the app's WebView or is handed
 * to the system browser.
 *
 * The problem this solves: Open WebUI's SSO login starts at a *same-origin* URL
 * (`<server>/oauth/<provider>/login`) which then 302-redirects out to the identity
 * provider. A policy that keeps only same-origin URLs in the WebView pushes that
 * redirect into the system browser, where the session cookie the callback sets is
 * unreachable — so login can never complete. Tool/MCP authorization in Open WebUI
 * 0.11 uses the same shape.
 *
 * So the rule is not "is this the same origin" but "is this navigation part of a
 * flow the server started". A redirect always is. A link the user tapped in chat
 * content never is.
 *
 * Deliberately free of Android imports so it can be unit tested on the JVM.
 */
class NavigationPolicy(serverUrl: String) {

    enum class Decision { WEBVIEW, EXTERNAL }

    private val serverOrigin: String? = originOf(serverUrl)

    /**
     * Origins we have followed a redirect into and not yet returned from — the
     * identity provider, and anything it bounces through. Insertion-ordered so
     * the oldest entry is the one evicted at the cap.
     */
    private val flowOrigins = LinkedHashSet<String>()

    /**
     * @param isRedirect   [android.webkit.WebResourceRequest.isRedirect]
     * @param hasGesture   [android.webkit.WebResourceRequest.hasGesture]
     * @param isMainFrame  [android.webkit.WebResourceRequest.isForMainFrame]
     */
    fun decide(
        url: String,
        isRedirect: Boolean,
        hasGesture: Boolean,
        isMainFrame: Boolean = true
    ): Decision {
        // Sub-frame loads (iframes) are page structure, not navigation. Never
        // eject one to the browser.
        if (!isMainFrame) return Decision.WEBVIEW

        // mailto:, tel:, intent:, market: ... nothing the WebView can render.
        val origin = originOf(url) ?: return Decision.EXTERNAL

        // Home. Any auth flow that was in progress has landed.
        if (origin == serverOrigin) {
            flowOrigins.clear()
            return Decision.WEBVIEW
        }

        // The server (or the IdP) sent us here. This is the OAuth dance.
        if (isRedirect) {
            remember(origin)
            return Decision.WEBVIEW
        }

        // Already inside the IdP: its own pages, forms and "choose an account"
        // taps are all gestured navigations that must not escape.
        if (origin in flowOrigins) return Decision.WEBVIEW

        // No user gesture, so the page navigated itself — a JS redirect, which
        // several IdPs use in place of a 302.
        if (!hasGesture) {
            remember(origin)
            return Decision.WEBVIEW
        }

        // A user-initiated jump to an unrelated origin: a citation, a link in a
        // model's answer, the Ko-fi button. That belongs in a real browser.
        return Decision.EXTERNAL
    }

    private fun remember(origin: String) {
        if (origin == serverOrigin) return
        if (flowOrigins.size >= MAX_FLOW_ORIGINS) {
            flowOrigins.iterator().let { if (it.hasNext()) { it.next(); it.remove() } }
        }
        flowOrigins.add(origin)
    }

    /** scheme://host[:port], or null for anything that is not http(s). */
    private fun originOf(url: String): String? {
        val uri = try { URI(url) } catch (e: Exception) { return null }
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase() ?: return null
        val defaultPort = if (scheme == "https") 443 else 80
        val port = if (uri.port == -1) defaultPort else uri.port
        return if (port == defaultPort) "$scheme://$host" else "$scheme://$host:$port"
    }

    private companion object {
        const val MAX_FLOW_ORIGINS = 8
    }
}
