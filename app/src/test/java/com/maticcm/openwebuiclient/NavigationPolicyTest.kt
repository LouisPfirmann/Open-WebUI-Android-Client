package com.maticcm.openwebuiclient

import com.maticcm.openwebuiclient.NavigationPolicy.Decision.EXTERNAL
import com.maticcm.openwebuiclient.NavigationPolicy.Decision.WEBVIEW
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationPolicyTest {

    private fun policy(server: String = "https://chat.example.com") = NavigationPolicy(server)

    // --- the flow this whole class exists for -------------------------------

    @Test
    fun `full oidc login round trip stays in the webview`() {
        val p = policy()

        // User taps "Sign in with OIDC". Same-origin backend route.
        assertEquals(
            WEBVIEW,
            p.decide("https://chat.example.com/oauth/oidc/login", isRedirect = false, hasGesture = true)
        )
        // Backend 302s out to the identity provider.
        assertEquals(
            WEBVIEW,
            p.decide("https://auth.example.com/realms/main/protocol/openid-connect/auth?client_id=owui",
                isRedirect = true, hasGesture = false)
        )
        // User types credentials and taps Sign In — a gestured navigation on the
        // IdP's own origin. This is the one the old code ejected to Chrome.
        assertEquals(
            WEBVIEW,
            p.decide("https://auth.example.com/realms/main/login-actions/authenticate",
                isRedirect = false, hasGesture = true)
        )
        // IdP redirects back to the callback.
        assertEquals(
            WEBVIEW,
            p.decide("https://chat.example.com/oauth/oidc/callback?code=abc&state=xyz",
                isRedirect = true, hasGesture = false)
        )
    }

    @Test
    fun `idp that uses a js redirect instead of a 302 stays in the webview`() {
        val p = policy()
        assertEquals(
            WEBVIEW,
            p.decide("https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
                isRedirect = false, hasGesture = false)
        )
        // and its follow-up gestured navigations too
        assertEquals(
            WEBVIEW,
            p.decide("https://login.microsoftonline.com/common/login", isRedirect = false, hasGesture = true)
        )
    }

    @Test
    fun `returning home ends the flow so the idp origin is no longer trusted`() {
        val p = policy()
        p.decide("https://auth.example.com/authorize", isRedirect = true, hasGesture = false)
        p.decide("https://chat.example.com/", isRedirect = true, hasGesture = false)

        // A later user-tapped link to that same host is now just a link.
        assertEquals(
            EXTERNAL,
            p.decide("https://auth.example.com/some-article", isRedirect = false, hasGesture = true)
        )
    }

    // --- links that should still leave the app ------------------------------

    @Test
    fun `user tapped link in a model answer opens externally`() {
        assertEquals(
            EXTERNAL,
            policy().decide("https://en.wikipedia.org/wiki/Ollama", isRedirect = false, hasGesture = true)
        )
    }

    @Test
    fun `non http schemes always go to the system`() {
        val p = policy()
        assertEquals(EXTERNAL, p.decide("mailto:someone@example.com", isRedirect = false, hasGesture = true))
        assertEquals(EXTERNAL, p.decide("tel:+15551234", isRedirect = false, hasGesture = true))
        assertEquals(EXTERNAL, p.decide("intent://scan#Intent;scheme=zxing;end", isRedirect = false, hasGesture = true))
    }

    // --- origin comparison ---------------------------------------------------

    @Test
    fun `server origin matches regardless of path port spelling or case`() {
        val p = NavigationPolicy("http://192.168.1.50:3000/")
        assertEquals(WEBVIEW, p.decide("http://192.168.1.50:3000/c/abc", isRedirect = false, hasGesture = true))
        assertEquals(WEBVIEW, p.decide("http://192.168.1.50:3000", isRedirect = false, hasGesture = true))
        assertEquals(EXTERNAL, p.decide("http://192.168.1.50:8080/", isRedirect = false, hasGesture = true))
    }

    @Test
    fun `default ports are equivalent to explicit ones`() {
        val p = NavigationPolicy("https://chat.example.com")
        assertEquals(WEBVIEW, p.decide("https://chat.example.com:443/admin", isRedirect = false, hasGesture = true))
        assertEquals(WEBVIEW, p.decide("https://CHAT.example.com/admin", isRedirect = false, hasGesture = true))
    }

    @Test
    fun `a server url saved with a subpath still matches its origin`() {
        val p = NavigationPolicy("https://example.com/openwebui")
        assertEquals(WEBVIEW, p.decide("https://example.com/openwebui/c/1", isRedirect = false, hasGesture = true))
    }

    // --- structural ---------------------------------------------------------

    @Test
    fun `subframe loads are never ejected`() {
        assertEquals(
            WEBVIEW,
            policy().decide("https://www.youtube.com/embed/x", isRedirect = false, hasGesture = true, isMainFrame = false)
        )
    }

    @Test
    fun `flow origins are capped so a redirect chain cannot grow without bound`() {
        val p = policy()
        repeat(12) { i ->
            p.decide("https://hop$i.example.org/", isRedirect = true, hasGesture = false)
        }
        // The earliest hops have been evicted; a tap on one is treated as a link.
        assertEquals(EXTERNAL, p.decide("https://hop0.example.org/x", isRedirect = false, hasGesture = true))
        // The most recent is still trusted.
        assertEquals(WEBVIEW, p.decide("https://hop11.example.org/x", isRedirect = false, hasGesture = true))
    }
}
