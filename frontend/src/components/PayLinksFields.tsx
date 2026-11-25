import { Field } from './ui'
import { inputClass } from './styles'

/** 송금 링크 입력 두 칸 (내 정보 · 정산 만들기에서 같이 쓴다). 계좌번호는 받지 않는다 */
export function PayLinksFields({
  tossId,
  kakaoPayUrl,
  onTossIdChange,
  onKakaoPayUrlChange,
}: {
  tossId: string
  kakaoPayUrl: string
  onTossIdChange: (v: string) => void
  onKakaoPayUrlChange: (v: string) => void
}) {
  return (
    <>
      <Field label="토스 아이디" optional hint="토스 앱에서 만든 토스아이디. 원우가 누르면 금액이 채워진 송금 화면이 열려요">
        {({ id, describedBy }) => (
          <input
            id={id}
            aria-describedby={describedBy}
            className={inputClass}
            placeholder="toss.me/아이디"
            autoComplete="off"
            autoCapitalize="none"
            spellCheck={false}
            maxLength={60}
            value={tossId}
            onChange={(e) => onTossIdChange(e.target.value)}
          />
        )}
      </Field>
      <Field label="카카오페이 송금 링크" optional hint="카카오페이 송금코드 링크. 금액은 원우가 직접 넣어요">
        {({ id, describedBy }) => (
          <input
            id={id}
            aria-describedby={describedBy}
            className={inputClass}
            placeholder="qr.kakaopay.com/…"
            inputMode="url"
            autoComplete="off"
            autoCapitalize="none"
            spellCheck={false}
            maxLength={120}
            value={kakaoPayUrl}
            onChange={(e) => onKakaoPayUrlChange(e.target.value)}
          />
        )}
      </Field>
    </>
  )
}
