# Room и Ktor
-keep class ru.tolmachev.ogetrainer.data.** { *; }
-dontwarn org.slf4j.**
-dontwarn io.ktor.**

# Tink (EncryptedSharedPreferences) ссылается на аннотации errorprone — их нет в classpath.
-dontwarn com.google.errorprone.annotations.**
