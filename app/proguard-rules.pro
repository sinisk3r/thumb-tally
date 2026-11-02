# R8 rules for release builds. Room, Compose and Coil ship their own consumer rules.
# Keep the accessibility service and receivers: Android finds them by name from the manifest (R8 keeps
# manifest classes already; this is belt and braces).
-keep class com.srik.shortstracker.tracker.ShortsAccessibilityService { *; }
-keep class com.srik.shortstracker.tracker.TrackingGuard$CheckReceiver { *; }
