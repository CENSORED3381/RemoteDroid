# RemoteDroid - regras ProGuard/R8

# Kadb usa reflexao para protocolos ADB/criptografia - manter intacto
-keep class com.flyfishxu.kadb.** { *; }
-dontwarn com.flyfishxu.kadb.**

# BouncyCastle (usado internamente por bibliotecas de ADB para RSA)
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

-keepattributes *Annotation*
-keepattributes Signature
