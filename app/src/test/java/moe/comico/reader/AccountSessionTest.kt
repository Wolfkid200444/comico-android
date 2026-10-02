package moe.comico.reader

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class AccountSessionTest {
    @Test fun sessionCookiesNeverReachImageProvidersOrInsecureRequests() {
        val cookie = Cookie.Builder().name("session").value("test").domain("comico.moe").path("/").expiresAt(2000).build()
        assertEquals(listOf(cookie),accountCookiesFor("https://comico.moe/api/auth/get-session".toHttpUrl(),listOf(cookie),1000))
        for(url in listOf("https://images.comico.moe/page.jpg","https://mangadex.org/page.jpg","http://comico.moe/api/auth/get-session"))
            assertTrue(accountCookiesFor(url.toHttpUrl(),listOf(cookie),1000).isEmpty())
        assertTrue(accountCookiesFor("https://comico.moe/".toHttpUrl(),listOf(cookie),2001).isEmpty())
    }
    @Test fun loginChoosesEmailOrUsernameWithoutChangingPassword() {
        val email = signInPayload(" reader@example.com "," spaced password ")
        assertEquals("/api/auth/sign-in/email",email.first)
        assertEquals("reader@example.com",email.second.getString("email"))
        assertEquals(" spaced password ",email.second.getString("password"))
        val username = signInPayload(" reader ","password")
        assertEquals("/api/auth/sign-in/username",username.first)
        assertEquals("reader",username.second.getString("username"))
        assertFalse(username.second.has("email"))
    }
}
