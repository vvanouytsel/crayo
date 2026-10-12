package app.crayo.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.crayo.color.OpenAi
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Whether a colored example is printed with the coloring pages. Chosen on every print, starting at the last choice. */
enum class ExampleMode(val label: String) { Off("None"), Small("Small"), Page("Own page") }

/** How strong the example's colors are. Lighter colors use less ink. */
enum class InkLevel(val label: String, val wash: Float) { Full("Full", 0f), Soft("Soft", 0.45f), Light("Light", 0.70f) }

/** The app's settings, kept on the phone. The OpenAI key is stored encrypted. */
class Settings(context: Context) {
    private val store = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var exampleMode: ExampleMode
        get() = ExampleMode.entries.firstOrNull { it.name == store.getString("exampleMode", null) } ?: ExampleMode.Off
        set(value) = store.edit().putString("exampleMode", value.name).apply()

    var ink: InkLevel
        get() = InkLevel.entries.firstOrNull { it.name == store.getString("ink", null) } ?: InkLevel.Full
        set(value) = store.edit().putString("ink", value.name).apply()

    var model: String
        get() = store.getString("model", null)?.takeIf { it.isNotBlank() } ?: OpenAi.DEFAULT_MODEL
        set(value) = store.edit().putString("model", value.trim()).apply()

    var server: String
        get() = store.getString("server", null)?.takeIf { it.isNotBlank() } ?: OpenAi.DEFAULT_SERVER
        set(value) = store.edit().putString("server", value.trim()).apply()

    var openAiKey: String
        get() = store.getString("openAiKey", null)?.let { Secret.open(it) } ?: ""
        set(value) = store.edit().putString("openAiKey", if (value.isBlank()) null else Secret.seal(value.trim())).apply()
}

/** Encrypts small secrets with a key that never leaves the phone's secure hardware. */
private object Secret {
    private const val ALIAS = "crayo-settings"

    private fun key(): SecretKey {
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keystore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    fun seal(text: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.doFinal(text.toByteArray())
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    fun open(stored: String): String? = try {
        val (iv, sealed) = stored.split(":").map { Base64.decode(it, Base64.NO_WRAP) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        String(cipher.doFinal(sealed))
    } catch (e: Exception) {
        null
    }
}
