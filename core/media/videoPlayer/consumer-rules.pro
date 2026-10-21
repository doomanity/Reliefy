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

# Protobuf lite fills message fields through reflection. Without these rules R8 assumes every field
# keeps its default value and folds the SABR parser into nonsense (all lengths, ids and statuses 0).
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keep class video_streaming.** { *; }
-keep class misc.** { *; }
-keep class org.schabi.newpipe.extractor.services.youtube.protos.** { *; }
