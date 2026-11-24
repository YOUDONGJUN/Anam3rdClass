package kr.anam3.app;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // 앱 전용 기능 (카카오톡 로그인 · 앱 정보): AnamNativePlugin.kt
        registerPlugin(AnamNativePlugin.class);
        super.onCreate(savedInstanceState);
    }
}
