/** 기수·학과를 "61기 · 인융" 형태로 (없는 값은 빼고) */
export function affiliation(cohortLabel: string | null | undefined, departmentLabel: string | null | undefined) {
  return [cohortLabel, departmentLabel].filter(Boolean).join(' · ')
}
