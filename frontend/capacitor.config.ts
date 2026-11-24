import type { CapacitorConfig } from '@capacitor/cli'

// 안드로이드 앱 (Capacitor). 앱은 화면을 서버에서 불러온다 → 웹을 배포하면 앱에도 바로 반영된다.
// 앱 안에 들어가는 건 native-shell/ (서버에 못 붙을 때 보이는 안내 화면)뿐이다.
//
// 내 PC 서버로 시험할 때 (에뮬레이터에서 10.0.2.2 = 내 PC):
//   CAP_SERVER_URL=http://10.0.2.2:8080 npx cap sync android
// 운영 주소로 되돌릴 때는 CAP_SERVER_URL 없이 다시 npx cap sync android
const serverUrl = (process.env.CAP_SERVER_URL ?? 'https://anam3.onrender.com').replace(/\/$/, '')

const BOARD = '#1b2533' // 안내판 색 (index.css --board) — 스플래시·로딩 바탕

const config: CapacitorConfig = {
  appId: 'kr.anam3.app', // 출시 뒤에는 못 바꾼다
  appName: '안암3교시',
  webDir: 'native-shell',
  backgroundColor: BOARD,
  server: {
    url: serverUrl,
    cleartext: serverUrl.startsWith('http://'),
    errorPath: 'offline.html',
  },
  android: {
    // 서버·화면이 앱에서 열었는지 알 수 있게 (User-Agent 끝에 붙는다)
    appendUserAgent: 'Anam3App',
  },
  plugins: {
    SplashScreen: {
      // 화면이 준비되면 웹이 바로 닫는다 (lib/native.ts). 늦어도 3초 뒤에는 닫힌다
      launchShowDuration: 3000,
      launchAutoHide: true,
      backgroundColor: BOARD,
      showSpinner: false,
    },
    PushNotifications: {
      // 앱을 보고 있을 때 온 알림도 알림창에 띄운다
      presentationOptions: ['badge', 'sound', 'alert'],
    },
    SystemBars: {
      // 위쪽 상태바는 늘 어두운 안내판 위 → 흰 아이콘. 아래 내비게이션 바는 화면 밝기에 맞춰 웹이 바꾼다
      style: 'DARK',
      insetsHandling: 'css',
      initialViewportFitValueHint: 'cover',
    },
  },
}

export default config
