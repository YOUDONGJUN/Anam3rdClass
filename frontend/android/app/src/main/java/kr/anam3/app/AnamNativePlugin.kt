package kr.anam3.app

import android.os.Build
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
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
 */
@CapacitorPlugin(name = "AnamNative")
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
}
