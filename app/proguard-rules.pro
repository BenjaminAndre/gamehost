# Gamehost ships unminified for now; v0.1 has no release signing config and is
# distributed as a debug APK. Keep this file so the release build type stays valid.

# snakeyaml reflects over constructors, and references java.beans, which does not exist on
# Android. We only ever load with SafeConstructor — Maps, Lists and scalars, no user classes
# — so the bean paths are unreachable, but R8 still needs telling not to warn about them.
# Added with the dependency rather than the day minification is first switched on, when the
# breakage would be a mystery.
-keep class org.yaml.snakeyaml.** { *; }
-dontwarn org.yaml.snakeyaml.**
-dontwarn java.beans.**
