import { useSignupOptions } from '../api/queries'
import { Field } from './ui'
import { inputClass } from './styles'

interface Props {
  /** 기수·학과는 학교마다 다르다 */
  schoolId: number | null
  /** 선택 안 했으면 '' */
  cohort: string
  departmentId: number | null
  onCohortChange: (value: string) => void
  onDepartmentChange: (value: number) => void
  /** 기수를 직접 고칠 수 없다 (원우는 가입한 날만 — 내 정보) */
  cohortLocked?: boolean
  /** 지금 학과 이름 — 숨긴 학과면 목록에 없어서 따로 보여 준다 */
  currentDepartmentLabel?: string | null
}

/** 기수(최신 → 1기)와 학과 선택. 가입 화면과 관리 화면(원우 프로필 고치기)에서 같이 쓴다 */
export function AffiliationFields({
  schoolId,
  cohort,
  departmentId,
  onCohortChange,
  onDepartmentChange,
  cohortLocked = false,
  currentDepartmentLabel = null,
}: Props) {
  const { data, isPending, isError } = useSignupOptions()
  const school = data?.schools.find((s) => s.id === schoolId)
  const latest = school?.latestCohort ?? 0
  const cohorts = Array.from({ length: latest }, (_, i) => latest - i)
  // 학과마다 대학원이 다르면(연세대: 인공지능융합대학원 · 공학대학원) 학과 옆에 대학원을 적는다
  const gradOf = (d: { graduateSchool: string | null }) => d.graduateSchool ?? school?.graduateSchool ?? null
  const mixed = new Set((school?.departments ?? []).map(gradOf)).size > 1

  return (
    <>
      <Field
        label="기수"
        error={isError ? '기수 목록을 불러오지 못했어요. 새로고침해 주세요' : null}
        hint={
          cohortLocked
            ? '기수는 가입한 날에만 직접 고칠 수 있어요. 바꾸려면 교수\u2060·\u2060대학원장에게 요청해 주세요'
            : school
              ? `${school.shortName}는 1기부터 ${latest}기까지 있어요`
              : undefined
        }
      >
        {({ id, describedBy }) => (
          <select
            id={id}
            aria-describedby={describedBy}
            className={inputClass}
            value={cohort}
            disabled={isPending || cohortLocked}
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
        <legend className="mb-2 text-[15px] font-semibold text-ink">
          학과
          {!mixed && school?.graduateSchool && (
            <span className="ml-1.5 text-sm font-medium text-ink-3">{school.graduateSchool}</span>
          )}
        </legend>
        {departmentId !== null && school && !school.departments.some((d) => d.id === departmentId) && (
          <p className="mb-2 rounded-xl bg-surface-2 px-4 py-3 text-sm text-ink-2">
            지금 학과: <b className="text-ink">{currentDepartmentLabel ?? '숨긴 학과'}</b> (지금은 고를 수 없는 학과예요)
          </p>
        )}
        <div className="grid gap-2">
          {(school?.departments ?? []).map((d) => {
            const active = departmentId === d.id
            return (
              <label
                key={d.id}
                className={`flex min-h-12 cursor-pointer items-center gap-3 rounded-xl border px-4 text-[15px] font-semibold transition-colors has-focus-visible:ring-2 has-focus-visible:ring-accent ${
                  active ? 'border-board bg-board text-on-board' : 'border-line-strong bg-surface text-ink hover:border-ink-3'
                }`}
              >
                <input
                  type="radio"
                  name="department"
                  value={d.id}
                  checked={active}
                  onChange={() => onDepartmentChange(d.id)}
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
                <span className="min-w-0 py-2">
                  {d.name}
                  {mixed && gradOf(d) && (
                    <span className={`ml-1.5 text-sm font-medium ${active ? 'opacity-80' : 'text-ink-3'}`}>{gradOf(d)}</span>
                  )}
                </span>
              </label>
            )
          })}
          {school && school.departments.length === 0 && (
            <p className="rounded-xl bg-surface-2 px-4 py-3 text-sm text-ink-2">
              등록된 학과가 없어요. 대학원장에게 알려 주세요.
            </p>
          )}
        </div>
      </fieldset>
    </>
  )
}
