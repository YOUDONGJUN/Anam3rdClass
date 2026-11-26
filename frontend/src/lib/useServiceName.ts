import { useAuth } from '../auth/AuthContext'
import { serviceName } from './credit'

/** 지금 보이는 화면 이름: 로그인했으면 우리 학교 동네 + 3교시 (신촌3교시), 아니면 공식 이름 */
export function useServiceName(): string {
  const { member } = useAuth()
  return serviceName(member?.school)
}
