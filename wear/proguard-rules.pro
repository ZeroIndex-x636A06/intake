# kotlinx.serialization: keep generated serializers for the shared protocol.
-keepclassmembers class com.zeroindex.intake.wearprotocol.** { *** Companion; *** serializer(...); }
-keep,includedescriptorclasses class com.zeroindex.intake.wearprotocol.**$$serializer { *; }
