import type { Department } from '../api/types'
import { useSignupOptions } from '../api/queries'
import { Field } from './ui'
import { inputClass } from './styles'

interface Props {
  /** 선택 안 했으면 '' */
  cohort: string
  department: Department | ''
  onCohortChange: (value: string) => void
  onDepartmentChange: (value: Department) => void
}

/** 기수(최신 → 1기)와 학과 선택. 가입 화면과 내 정보 수정에서 같이 쓴다 */
export function AffiliationFields({ cohort, department, onCohortChange, onDepartmentChange }: Props) {
  const { data, isPending, isError } = useSignupOptions()
  const cohorts = data ? Array.from({ length: data.latestCohort }, (_, i) => data.latestCohort - i) : []

  return (
    <>
      <Field label="기수" error={isError ? '기수 목록을 불러오지 못했어요. 새로고침해 주세요' : null}>
        {({ id }) => (
          <select
            id={id}
            className={inputClass}
            value={cohort}
            disabled={isPending}
            onChange={(e) => onCohortChange(e.target.value)}
          >
            <option value="" disabled>
              {isPending ? '불러오는 중…' : '기수 선택'}
            </option>
            {cohorts.map((c) => (
              <option key={c} value={c}>
                {c}기
              </option>
            ))}
          </select>
        )}
      </Field>

      <fieldset>
        <legend className="mb-2 text-[15px] font-semibold text-ink">학과</legend>
        <div className="grid gap-2">
          {(data?.departments ?? []).map((d) => {
            const active = department === d.code
            return (
              <label
                key={d.code}
                className={`flex min-h-12 cursor-pointer items-center gap-3 rounded-xl border px-4 text-[15px] font-semibold transition-colors has-focus-visible:ring-2 has-focus-visible:ring-accent ${
                  active ? 'border-board bg-board text-on-board' : 'border-line-strong bg-surface text-ink hover:border-ink-3'
                }`}
              >
                <input
                  type="radio"
                  name="department"
                  value={d.code}
                  checked={active}
                  onChange={() => onDepartmentChange(d.code)}
                  className="sr-only"
                />
                <span
                  aria-hidden
                  className={`flex size-5 shrink-0 items-center justify-center rounded-full border-2 ${
                    active ? 'border-accent' : 'border-line-strong'
                  }`}
                >
                  {active && <span className="size-2.5 rounded-full bg-accent" />}
                </span>
                {d.label}
              </label>
            )
          })}
        </div>
      </fieldset>
    </>
  )
}
