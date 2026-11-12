# kotlinx.serialization keeps generated serializers via its bundled rules; keep DTO names readable in crashes.
-keepattributes *Annotation*, InnerClasses, Signature
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
