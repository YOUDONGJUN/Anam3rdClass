package kr.anam3.app

import android.Manifest
import android.content.ContentValues
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import com.getcapacitor.JSObject
import com.getcapacitor.PermissionState
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import java.io.File
import java.io.IOException
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.KakaoSdk
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient

/**
 * 웹(frontend/src/lib/native.ts)이 부르는 앱 기능.
 *
 * - info: 앱 버전, 푸시를 쓸 수 있는지(google-services.json 을 넣고 빌드했는지), 카카오 키가 있는지
 * - kakaoLogin: 카카오톡 앱(없으면 카카오 계정 창)으로 로그인하고 카카오 액세스 토큰을 웹에 준다.
 *   웹은 이 토큰을 서버(POST /api/auth/kakao/app)로 보내고, 서버가 우리 앱의 토큰인지 카카오에 확인한다.
 * - savePhoto: 사진을 갤러리(사진/안암3교시)에 저장한다. 안드로이드 9 이하는 저장 권한을 먼저 묻는다
 */
@CapacitorPlugin(
    name = "AnamNative",
    permissions = [Permission(alias = "photos", strings = [Manifest.permission.WRITE_EXTERNAL_STORAGE])],
)
class AnamNativePlugin : Plugin() {

    private val kakaoKey: String = BuildConfig.KAKAO_NATIVE_APP_KEY

    override fun load() {
        if (kakaoKey.isNotEmpty()) {
            KakaoSdk.init(context.applicationContext, kakaoKey)
        }
    }

    @PluginMethod
    fun info(call: PluginCall) {
        val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
        val build = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkg.longVersionCode else {
            @Suppress("DEPRECATION")
            pkg.versionCode.toLong()
        }
        // google-services.json 이 있으면 빌드할 때 google_app_id 문자열이 생기고 Firebase 가 켜진다.
        // 없는데 푸시를 등록하면 앱이 꺼지므로 웹이 먼저 물어본다
        val firebaseId = context.resources.getIdentifier("google_app_id", "string", context.packageName)
        val result = JSObject()
        result.put("version", pkg.versionName ?: "")
        result.put("build", build)
        result.put("pushAvailable", firebaseId != 0)
        result.put("kakaoAvailable", kakaoKey.isNotEmpty())
        call.resolve(result)
    }

    /** data: 사진(base64) · mimeType · fileName */
    @PluginMethod
    fun savePhoto(call: PluginCall) {
        if (call.getString("data").isNullOrEmpty()) {
            call.reject("저장할 사진이 없어요", "NO_DATA")
            return
        }
        // 안드로이드 10 부터는 권한 없이 갤러리에 넣을 수 있다
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && getPermissionState("photos") != PermissionState.GRANTED) {
            requestPermissionForAlias("photos", call, "savePhotoPermission")
            return
        }
        writePhoto(call)
    }

    @PermissionCallback
    private fun savePhotoPermission(call: PluginCall) {
        if (getPermissionState("photos") == PermissionState.GRANTED) {
            writePhoto(call)
        } else {
            call.reject("사진을 저장하려면 저장 권한을 허용해 주세요", "PERMISSION_DENIED")
        }
    }

    private fun writePhoto(call: PluginCall) {
        val mime = call.getString("mimeType") ?: "image/jpeg"
        val name = call.getString("fileName") ?: "anam3-${System.currentTimeMillis()}.jpg"
        try {
            val bytes = Base64.decode(call.getString("data"), Base64.DEFAULT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, mime)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("갤러리에 자리를 만들지 못했어요")
                (resolver.openOutputStream(uri) ?: throw IOException("갤러리에 쓸 수 없어요")).use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
                dir.mkdirs()
                val file = File(dir, name)
                file.writeBytes(bytes)
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null)
            }
            call.resolve()
        } catch (e: Exception) {
            call.reject("사진을 저장하지 못했어요", "SAVE_FAILED", e)
        }
    }

    @PluginMethod
    fun kakaoLogin(call: PluginCall) {
        if (kakaoKey.isEmpty()) {
            call.reject("카카오 로그인이 아직 준비되지 않았어요. 관리자에게 알려 주세요", "NOT_CONFIGURED")
            return
        }
        val activity = activity
        if (activity == null) {
            call.reject("앱을 다시 열어 주세요", "NO_ACTIVITY")
            return
        }
        val client = UserApiClient.instance
        val withAccount: (OAuthToken?, Throwable?) -> Unit = { token, error -> finish(call, token, error) }
        activity.runOnUiThread {
            if (client.isKakaoTalkLoginAvailable(activity)) {
                client.loginWithKakaoTalk(activity) { token, error ->
                    when {
                        // 카카오톡에서 "취소" → 카카오 계정 창을 또 띄우지 않는다
                        isCancelled(error) -> finish(call, null, error)
                        // 카카오톡에 연결된 카카오 계정이 없는 경우 등 → 카카오 계정으로
                        error != null -> client.loginWithKakaoAccount(activity, callback = withAccount)
                        else -> finish(call, token, null)
                    }
                }
            } else {
                client.loginWithKakaoAccount(activity, callback = withAccount)
            }
        }
    }

    private fun isCancelled(error: Throwable?): Boolean =
        error is ClientError && error.reason == ClientErrorCause.Cancelled

    private fun finish(call: PluginCall, token: OAuthToken?, error: Throwable?) {
        when {
            isCancelled(error) -> call.reject("카카오 로그인을 취소했어요", "CANCELLED")
            error != null || token == null -> call.reject("카카오 로그인에 실패했어요. 다시 시도해 주세요", "FAILED")
            else -> {
                val result = JSObject()
                result.put("accessToken", token.accessToken)
                call.resolve(result)
            }
        }
    }

    private companion object {
        /** 갤러리 앨범 이름 (사진/안암3교시) */
        const val ALBUM = "안암3교시"
    }
}
