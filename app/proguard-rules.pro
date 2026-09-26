# SiteSweep ProGuard Rules
# Keep TFLite / LiteRT classes required for on-device inference
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-keep class org.tensorflow.lite.nnapi.** { *; }

# Keep Room generated code
-keep class com.sitesweep.data.local.** { *; }
