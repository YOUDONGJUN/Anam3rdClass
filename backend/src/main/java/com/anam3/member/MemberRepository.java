package com.anam3.member;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByKakaoId(Long kakaoId);

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    /** 예전 아이디 (소문자로 저장돼 있다) */
    Optional<Member> findByLoginId(String loginId);

    /** 아직 카카오로 이어지지 않은 예전 회원이 있는 학교 — 그 학교 가입 화면에만 "예전 계정 잇기"를 보여 준다 */
    @Query("""
            select distinct m.school.id from Member m
            where m.kakaoId is null and m.email is null and m.passwordHash is not null and m.status <> :withdrawn
            """)
    List<Long> findUnclaimedLegacySchoolIds(@Param("withdrawn") MemberStatus withdrawn);

    List<Member> findAllByRole(MemberRole role);

    /** 로그인 확인·내 정보 (학교·학과까지 한 번에) */
    @Query("select m from Member m join fetch m.school left join fetch m.department where m.id = :id")
    Optional<Member> findWithSchool(@Param("id") Long id);

    /** 관리 화면 원우 그리드: 대학원장은 모든 학교 (학교·학과까지 한 번에) */
    @Query("""
            select m from Member m join fetch m.school left join fetch m.department
            order by m.createdAt desc, m.id desc
            """)
    List<Member> findAllForAdmin();

    /** 관리 화면 원우 그리드: 교수는 자기 학교만 */
    @Query("""
            select m from Member m join fetch m.school left join fetch m.department
            where m.school.id = :schoolId
            order by m.createdAt desc, m.id desc
            """)
    List<Member> findAllForAdmin(@Param("schoolId") Long schoolId);

    long countByStatusNot(MemberStatus status);

    long countBySchoolIdAndStatusNot(Long schoolId, MemberStatus status);

    /** 랭킹: 활동 중인 원우 모두 (학교·학과까지 한 번에) */
    @Query("select m from Member m join fetch m.school left join fetch m.department where m.status = :active")
    List<Member> findAllActiveWithSchool(@Param("active") MemberStatus active);

    /** 랭킹 배지: 이 중 랭킹에 나오는 사람 (활동 중 + 랭킹에 안 나오기를 켜지 않음) */
    @Query("select m.id from Member m where m.id in :ids and m.status = :active and m.rankingHidden = false")
    List<Long> findRankedIds(@Param("ids") java.util.Collection<Long> ids, @Param("active") MemberStatus active);
}
