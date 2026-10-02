package moe.comico.reader

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Session cookies belong only to Comico, never to third-party image providers. */
fun accountCookiesFor(url: HttpUrl, cookies: List<Cookie>, now: Long = System.currentTimeMillis()): List<Cookie> =
    if(url.scheme != "https" || url.host != "comico.moe") emptyList() else cookies.filter { it.expiresAt > now && it.matches(url) }

class AccountCookieJar(context: Context): CookieJar {
    private val prefs = context.getSharedPreferences("account_session",Context.MODE_PRIVATE)
    private var loaded = false
    private var cookies = emptyList<Cookie>()
    private val origin = BASE_URL.toHttpUrl()
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("comico-account-session",null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("comico-account-session",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun load() {
        if(loaded) return
        loaded = true
        val encrypted = prefs.getString("cookies",null) ?: return
        cookies = runCatching {
            val record = JSONObject(encrypted)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(record.getString("iv"),Base64.NO_WRAP)))
            val text = String(cipher.doFinal(Base64.decode(record.getString("value"),Base64.NO_WRAP)),Charsets.UTF_8)
            JSONArray(text).strings().mapNotNull { Cookie.parse(origin,it) }.filter { it.expiresAt > System.currentTimeMillis() }
        }.getOrElse { prefs.edit().clear().apply();emptyList() }
    }
    private fun persist() {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
        val encrypted = cipher.doFinal(JSONArray(cookies.map { it.toString() }).toString().toByteArray(Charsets.UTF_8))
        prefs.edit().putString("cookies",JSONObject().put("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP)).put("value",Base64.encodeToString(encrypted,Base64.NO_WRAP)).toString()).apply()
    }
    @Synchronized override fun saveFromResponse(url: HttpUrl, incoming: List<Cookie>) {
        if(url.scheme != "https" || url.host != "comico.moe") return
        load()
        val accepted = incoming.filter { it.domain == "comico.moe" }
        cookies = (cookies.filterNot { existing -> accepted.any { it.name == existing.name && it.path == existing.path && it.domain == existing.domain } } + accepted).filter { it.expiresAt > System.currentTimeMillis() }
        persist()
    }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if(url.scheme != "https" || url.host != "comico.moe") return emptyList()
        load()
        return accountCookiesFor(url,cookies)
    }
    @Synchronized fun clear() { cookies = emptyList();loaded = true;prefs.edit().clear().apply() }
}

data class AccountUser(val id: String, val name: String, val username: String, val email: String, val verified: Boolean)
data class AccountState(val user: AccountUser? = null, val loading: Boolean = false, val error: String? = null, val message: String? = null)
fun JSONObject.accountUser() = AccountUser(getString("id"),optString("name"),optString("username").takeUnless { it == "null" }.orEmpty(),optString("email"),optBoolean("emailVerified"))
fun signInPayload(identifier: String, password: String): Pair<String,JSONObject> = if(identifier.contains('@')) "/api/auth/sign-in/email" to JSONObject().put("email",identifier.trim()).put("password",password) else "/api/auth/sign-in/username" to JSONObject().put("username",identifier.trim()).put("password",password)
fun accountErrorMessage(code: String, fallback: String) = when(code) {
    "EMAIL_NOT_VERIFIED" -> "Verify your email before signing in. Check your inbox for Comico's verification message."
    "INVALID_EMAIL_OR_PASSWORD", "INVALID_USERNAME_OR_PASSWORD", "USER_NOT_FOUND", "INVALID_PASSWORD" -> "The email, username, or password is incorrect."
    else -> fallback
}
