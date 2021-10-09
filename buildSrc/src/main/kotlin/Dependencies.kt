@file:Suppress("MemberVisibilityCanBePrivate", "SpellCheckingInspection")

import org.gradle.api.JavaVersion

object Config{
    val compileSdkVersion = 30
    val minSdkVersion = 21
    val targetSdkVersion = 30
    val buildToolsVersion = "29.0.3"
    val sourceCompatibilityVersion = JavaVersion.VERSION_1_8
    val targetCompatibilityVersion = JavaVersion.VERSION_1_8
    val versionCode = 108
    val versionName = "1.2.37"
}

object Versions {

    const val PLUGIN_GRADLE_VERSION="3.2.1"
    const val PLUGIN_DEXINFO_VERSION="0.1.2"
    const val PLUGIN_SCALA_VERSION="1.5.0"
    const val PLUGIN_GMS_VERSION="3.1.1"
    const val PLUGIN_GRGIT_VERSION="3.0.0"
    const val PLUGIN_AGCP_VERSION="1.4.1.300"

    const val KOTLIN_VERSION = "1.3.60"
    const val SCALA_VERSION = "2.11.12"

    const val ANDROIDX_APP_COMPAT_VERSION = "1.1.0"
    const val ANDROIDX_LEGACY_V4_VERSION = "1.0.0"
    const val ANDROIDX_LEGACY_V13_VERSION = "1.0.0"
    const val ANDROIDX_MULTIDEX_VERSION = "2.0.0"
    const val ANDROIDX_MATERIAL_VERSION = "1.0.0"
    const val ANDROIDX_RECYCLERVIEW_VERSION = "1.0.0"
    const val ANDROIDX_PREFERENCE_VERSION = "1.0.0"
    const val ANDROIDX_CARDVIEW_VERSION = "1.0.0"
    const val ANDROIDX_GRIDLAYOUT_VERSION = "1.0.0"
    const val ANDROIDX_ANNOTATION_VERSION = "1.1.0"
    const val ANDROIDX_CONSTRAINTLAYOUT_VERSION = "1.1.3"
    const val ANDROIDX_PAGING_VERSION = "2.0.0"
    const val ANDROIDX_WORK_RUNTIME_VERSION = "2.0.1"
    const val ANDROIDX_CORE_VERSION = "1.1.0"

    const val ANDROIDX_LIFECYCLE_VERSION = "2.0.0"

    const val GOOGLE_PLAY_SERVICES_VERSION = "17.0.0"
    const val GOOGLE_FIREBASE_MESSAGING_VERSION = "17.3.0"

    const val WIRE_AUDIO_VERSION = "1.209.0@aar"
    const val WIRE_ZMESSAGING_VERSION = "141.1.0-SNAPSHOT@aar"

    const val GSON_VERSION = "2.8.2"
    const val JUNIT_VERSION = "4.12"
    const val IMMERSIONBAR_VERSION = "3.0.0"

    const val SUPPORT_PREFERENCE_VERSION = "0.8.1"
    const val EVERNOTE_ANDROIDJOB_VERSION = "1.2.6"
    const val FACEBOOK_REBOUND_VERSION = "0.3.8"
    const val ATLASSIAN_COMMONMARK_VERSION = "0.11.0"
    const val JAVADEV_JNAAAR_VERSION = "4.4.0@aar"
    const val RXJAVA2_VERSION = "2.1.9"
    const val RXANDROID_VERSION = "2.0.2"
    const val RETROFIT_VERSION = "2.4.0"
    const val OKHTTP_VERSION = "3.10.0"
    const val ZXING_CORE_VERSION = "3.3.1"
    const val ZXING_ANDROID_CORE_VERSION = "3.3.0"
    const val ZXING_BARCODE_SCANNER_VERSION = "1.9"
    const val ORMLITE_VERSION = "5.1"
    const val BASEQUICKADAPTER_VERSION = "2.9.40"
    const val GLIDE_VERSION = "4.9.0"
    const val PRETTYTIME_VERSION = "4.0.1.Final"
    const val SWIPEDELMENULAYOUT_VERSION = "V1.3.0"
    const val PHOTOVIEW_VERSION = "2.1.3"
    const val PERCENT_VERSION = "1.1.1"
    const val STETHO_VERSION = "1.5.0"
    const val TIMMER_VERSION = "4.7.0"
    const val THREETENABP_VERSION = "1.1.1"
    const val YANZHENJIE_RECYCLERVIEW_VERSION = "1.3.2"
    const val SMARTREFRESHLAYOUT_VERSION = "1.1.2"
    const val GIF_DRAWABLE_VERSION = "1.2.19"
    const val LUBAN_VERSION = "1.1.8"
    const val NANOHTTPD_VERSION = "2.2.0"
    const val SHORTCUTBADGER_VERSION = "1.1.22"
    const val ANDROIDSVG_VERSION = "1.2.1"
    const val STICKYHEADERS_VERSION = "0.4.3@aar"
    const val ANIMATED_WEBP_VERSION = "1.9.0"
    const val JSOUP_VERSION = "1.12.1"
    const val ALIBABA_AROUTER_VERSION = "1.5.2"
    const val AROUTER_COMPILER_VERSION = "1.5.2"
    const val AROUTER_REGISTER_VERSION = "1.0.2"
    const val NABINBHANDARI_PERMISSIONS_VERSION = "3.8"
    const val AGORA_VERSION = "3.5.0.1"
    const val MATERIAL_DIALOGS = "3.1.1"
    const val LOTTIE_VERSION = "3.7.0"

}

object BuildDependencies {

    val junit = "junit:junit:${Versions.JUNIT_VERSION}"
    val immersionbar = "com.gyf.immersionbar:immersionbar:${Versions.IMMERSIONBAR_VERSION}"
    val immersionbarComponents = "com.gyf.immersionbar:immersionbar-components:${Versions.IMMERSIONBAR_VERSION}"
    val immersionbarKtx = "com.gyf.immersionbar:immersionbar-ktx:${Versions.IMMERSIONBAR_VERSION}"

    val gson = "com.google.code.gson:gson:${Versions.GSON_VERSION}"
    val scalaLibrary = "org.scala-lang:scala-library:${Versions.SCALA_VERSION}"
    val scalaReflect = "org.scala-lang:scala-reflect:${Versions.SCALA_VERSION}"
    val audioNotifications = "com.wire:audio-notifications:${Versions.WIRE_AUDIO_VERSION}"
    val playServicesBase = "com.google.android.gms:play-services-base:${Versions.GOOGLE_PLAY_SERVICES_VERSION}"
    val playServicesMaps = "com.google.android.gms:play-services-maps:${Versions.GOOGLE_PLAY_SERVICES_VERSION}"
    val playServicesLocation = "com.google.android.gms:play-services-location:${Versions.GOOGLE_PLAY_SERVICES_VERSION}"
    val playServicesGcm = "com.google.android.gms:play-services-gcm:${Versions.GOOGLE_PLAY_SERVICES_VERSION}"
    val firebaseMessaging = "com.google.firebase:firebase-messaging:${Versions.GOOGLE_FIREBASE_MESSAGING_VERSION}"
    val preference = "net.xpece.android:support-preference:${Versions.SUPPORT_PREFERENCE_VERSION}"
    val androidJob = "com.evernote:android-job:${Versions.EVERNOTE_ANDROIDJOB_VERSION}"
    val rebound = "com.facebook.rebound:rebound:${Versions.FACEBOOK_REBOUND_VERSION}"
    val commonmark = "com.atlassian.commonmark:commonmark:${Versions.ATLASSIAN_COMMONMARK_VERSION}"
    val jna = "net.java.dev.jna:jna:${Versions.JAVADEV_JNAAAR_VERSION}"
    val rxjava2 = "io.reactivex.rxjava2:rxjava:${Versions.RXJAVA2_VERSION}"
    val rxAndroid = "io.reactivex.rxjava2:rxandroid:${Versions.RXANDROID_VERSION}"
    val retrofit = "com.squareup.retrofit2:retrofit:${Versions.RETROFIT_VERSION}"
    val retrofitAdapterRxjava2 = "com.squareup.retrofit2:adapter-rxjava2:${Versions.RETROFIT_VERSION}"
    val retrofitConverterGson = "com.squareup.retrofit2:converter-gson:${Versions.RETROFIT_VERSION}"
    val okhttp = "com.squareup.okhttp3:okhttp:${Versions.OKHTTP_VERSION}"
    val loggingInterceptor = "com.squareup.okhttp3:logging-interceptor:${Versions.OKHTTP_VERSION}"
    val zxingCore = "com.google.zxing:core:${Versions.ZXING_CORE_VERSION}"
    val zxingAndroidCore = "com.google.zxing:android-core:${Versions.ZXING_ANDROID_CORE_VERSION}"
    val ormliteCore = "com.j256.ormlite:ormlite-core:${Versions.ORMLITE_VERSION}"
    val ormLiteAndroid = "com.j256.ormlite:ormlite-android:${Versions.ORMLITE_VERSION}"
    val baseQuickAdapter = "com.github.CymChad:BaseRecyclerViewAdapterHelper:${Versions.BASEQUICKADAPTER_VERSION}"
    val glide = "com.github.bumptech.glide:glide:${Versions.GLIDE_VERSION}"
    val glideCompiler = "com.github.bumptech.glide:compiler:${Versions.GLIDE_VERSION}"
    val prettytime = "org.ocpsoft.prettytime:prettytime:${Versions.PRETTYTIME_VERSION}"
    val swipeDelMenuLayout = "com.github.mcxtzhang:SwipeDelMenuLayout:${Versions.SWIPEDELMENULAYOUT_VERSION}"
    val photoView = "com.github.chrisbanes:PhotoView:${Versions.PHOTOVIEW_VERSION}"
    val percent = "com.zhy:percent-support-extends:${Versions.PERCENT_VERSION}"
    val stetho = "com.facebook.stetho:stetho:${Versions.STETHO_VERSION}"
    val zmessaging = "com.wire:zmessaging-android:${Versions.WIRE_ZMESSAGING_VERSION}"
    val timber = "com.jakewharton.timber:timber:${Versions.TIMMER_VERSION}"
    val threetenabp = "com.jakewharton.threetenabp:threetenabp:${Versions.THREETENABP_VERSION}"
    val barcodescanner = "me.dm7.barcodescanner:zxing:${Versions.ZXING_BARCODE_SCANNER_VERSION}"
    val jsoup = "org.jsoup:jsoup:${Versions.JSOUP_VERSION}"
    val yanzhenjieRecyclerview = "com.yanzhenjie.recyclerview:support:${Versions.YANZHENJIE_RECYCLERVIEW_VERSION}"
    val smartRefreshLayout = "com.scwang.smartrefresh:SmartRefreshLayout:${Versions.SMARTREFRESHLAYOUT_VERSION}"
    val smartRefreshHeader = "com.scwang.smartrefresh:SmartRefreshHeader:${Versions.SMARTREFRESHLAYOUT_VERSION}"
    val gifDrawable = "pl.droidsonroids.gif:android-gif-drawable:${Versions.GIF_DRAWABLE_VERSION}"
    val luban = "top.zibin:Luban:${Versions.LUBAN_VERSION}"
    val nanohttpd = "org.nanohttpd:nanohttpd:${Versions.NANOHTTPD_VERSION}"
    val shortcutBadger = "me.leolin:ShortcutBadger:${Versions.SHORTCUTBADGER_VERSION}"
    val androidsvg = "com.caverock:androidsvg:${Versions.ANDROIDSVG_VERSION}"
    val stickyheaders = "com.timehop.stickyheadersrecyclerview:library:${Versions.STICKYHEADERS_VERSION}"
    val animatedWebp = "com.facebook.fresco:animated-webp:${Versions.ANIMATED_WEBP_VERSION}"
    val alibabaArouter = "com.alibaba:arouter-api:${Versions.ALIBABA_AROUTER_VERSION}"
    val alibabaCompiler = "com.alibaba:arouter-compiler:${Versions.AROUTER_COMPILER_VERSION}"
    val permissions = "com.nabinbhandari.android:permissions:${Versions.NABINBHANDARI_PERMISSIONS_VERSION}"
    val agoraVoice = "io.agora.rtc:voice-sdk:${Versions.AGORA_VERSION}"
    val dialogsLifecycle = "com.afollestad.material-dialogs:lifecycle:${Versions.MATERIAL_DIALOGS}"
    val dialogsBottomsheets = "com.afollestad.material-dialogs:bottomsheets:${Versions.MATERIAL_DIALOGS}"
    val dialogsCore = "com.afollestad.material-dialogs:core:${Versions.MATERIAL_DIALOGS}"
    val lottie = "com.airbnb.android:lottie:${Versions.LOTTIE_VERSION}"

}

object Kotlin {
    val kotlinStdlib = "org.jetbrains.kotlin:kotlin-stdlib:${Versions.KOTLIN_VERSION}"
    val kotlinStdlibJdk7 = "org.jetbrains.kotlin:kotlin-stdlib-jdk7:${Versions.KOTLIN_VERSION}"
    val coreKtx = "androidx.core:core-ktx:${Versions.ANDROIDX_CORE_VERSION}"
}

object AndroidX {
    val appCompat = "androidx.appcompat:appcompat:${Versions.ANDROIDX_APP_COMPAT_VERSION}"
    val legacyV4 = "androidx.legacy:legacy-support-v4:${Versions.ANDROIDX_LEGACY_V4_VERSION}"
    val legacyV13 = "androidx.legacy:legacy-support-v13:${Versions.ANDROIDX_LEGACY_V13_VERSION}"
    val multidex = "androidx.multidex:multidex:${Versions.ANDROIDX_MULTIDEX_VERSION}"
    val material = "com.google.android.material:material:${Versions.ANDROIDX_MATERIAL_VERSION}"
    val recyclerview = "androidx.recyclerview:recyclerview:${Versions.ANDROIDX_RECYCLERVIEW_VERSION}"
    val preference = "androidx.preference:preference:${Versions.ANDROIDX_PREFERENCE_VERSION}"
    val cardview = "androidx.cardview:cardview:${Versions.ANDROIDX_CARDVIEW_VERSION}"
    val gridlayout = "androidx.gridlayout:gridlayout:${Versions.ANDROIDX_GRIDLAYOUT_VERSION}"
    val annotationX = "androidx.annotation:annotation:${Versions.ANDROIDX_ANNOTATION_VERSION}"
    val constraintlayout = "androidx.constraintlayout:constraintlayout:${Versions.ANDROIDX_CONSTRAINTLAYOUT_VERSION}"
    val paging = "androidx.paging:paging-runtime:${Versions.ANDROIDX_PAGING_VERSION}"
    val work = "androidx.work:work-runtime:${Versions.ANDROIDX_WORK_RUNTIME_VERSION}"
    val media = "androidx.media:media:${Versions.ANDROIDX_APP_COMPAT_VERSION}"
}

object Lifecycle {
    val extensions = "androidx.lifecycle:lifecycle-extensions:${Versions.ANDROIDX_LIFECYCLE_VERSION}"
    val viewmodelktx = "androidx.lifecycle:lifecycle-viewmodel-ktx:${Versions.ANDROIDX_LIFECYCLE_VERSION}"
}

