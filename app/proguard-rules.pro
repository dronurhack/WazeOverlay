# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/sdk/tools/proguard/proguard-android.txt
-keepattributes *Annotation*
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.service.notification.NotificationListenerService
