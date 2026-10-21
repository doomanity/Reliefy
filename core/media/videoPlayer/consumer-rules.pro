# Cooley video player (ported from LibreTube)
-keepclassmembers class com.doomanity.cooley.video.potoken.PoTokenWebView {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.javascript.engine.** { *; }
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-keep class javax.script.** { *; }
-dontwarn javax.script.**
-keep class jdk.dynalink.** { *; }
-dontwarn jdk.dynalink.**
-dontwarn com.google.re2j.**
