# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ===========================================================================
# Room (androidx.room)
# ===========================================================================
# Room Entity: reflection으로 컬럼명-필드명 매핑. 필드명이 난독화되면 쿼리 결과 매핑 불가.
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Dao interface * { *; }

# Room KSP 생성 구현체 (_Impl 클래스)는 문자열 기반으로 인스턴스화됨.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class **_Impl { *; }
-keep class **_Impl$* { *; }

# AceChat 전용: Entity 패키지 명시적 보호 (위 어노테이션 규칙과 중복되지만 방어적으로 유지)
-keep class com.chloe.acechat.data.db.entity.** { *; }
-keep class com.chloe.acechat.data.db.**Dao { *; }
-keep class com.chloe.acechat.data.db.AceChatDatabase { *; }

# ===========================================================================
# LiteRT-LM (com.google.ai.edge.litertlm)
# ===========================================================================
# JNI native 메서드 및 브릿지 클래스 보호. native 심볼 이름은 변경 불가.
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
# JNI를 통해 Java/Kotlin 객체를 생성하는 경우 기본 생성자 보호
-keepclassmembers class com.google.ai.edge.litertlm.** {
    <init>(...);
}

# ===========================================================================
# Gemini SDK (com.google.ai.client.generativeai)
# ===========================================================================
# SDK 내부에서 Gson/Kotlinx-serialization으로 request/response 모델을 직렬화.
# 필드명이 난독화되면 JSON 파싱 실패.
-keep class com.google.ai.client.generativeai.** { *; }
-keepclassmembers class com.google.ai.client.generativeai.** {
    <fields>;
    <init>(...);
}

# Ktor (Gemini SDK 내부 HTTP 클라이언트 의존성)
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# ===========================================================================
# kotlinx-serialization
# ===========================================================================
# @Serializable 클래스의 companion object serializer()가 제거되면 런타임 오류 발생.
# Navigation type-safe route (NavRoutes.kt)의 @Serializable 클래스도 해당됨.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-dontwarn kotlinx.serialization.**
-keep class kotlinx.serialization.** { *; }
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * {
    static ** INSTANCE;
    static ** Companion;
    ** serializer();
    ** serializer(kotlin.reflect.KClass[]);
    *** $serializer;
}

# AceChat NavRoutes: type-safe navigation route 클래스 명시적 보호
-keep class com.chloe.acechat.presentation.navigation.** { *; }

# ===========================================================================
# WorkManager (ModelDownloadWorker)
# ===========================================================================
# WorkManager는 worker class name 문자열로 인스턴스화하므로 이름 유지 필수.
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.chloe.acechat.data.llm.ModelDownloadWorker { *; }

# ===========================================================================
# OkHttp (모델 다운로드 HTTP 클라이언트)
# ===========================================================================
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ===========================================================================
# Kotlin Coroutines / Flow
# ===========================================================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ===========================================================================
# Jetpack Compose / Navigation
# ===========================================================================
# Navigation Compose 2.9.0 type-safe routes: serialization 규칙 위에서 이미 커버됨.
# Compose 자체는 별도 ProGuard 규칙 불필요 (compose-compiler-plugin이 처리).
-dontwarn androidx.navigation.**

# ===========================================================================
# 공통 Kotlin/JVM 보호 규칙
# ===========================================================================
# Kotlin data class의 component 함수 및 copy()는 reflection 없이 직접 호출되므로
# 일반적으로 난독화 무방. 단, Room Entity는 위 규칙에서 이미 전체 보호.
-keepattributes Signature
-keepattributes Exceptions
