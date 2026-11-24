package com.anam3.meetup;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetupRepository extends JpaRepository<Meetup, Long> {

    /** 참여/나가기 동시 요청으로 정원이 넘치지 않게 행을 잠그고 가져온다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Meetup m where m.id = :id")
    Optional<Meetup> findByIdForUpdate(@Param("id") Long id);

    @Query("select m from Meetup m join fetch m.host join fetch m.school where m.id = :id")
    Optional<Meetup> findWithHost(@Param("id") Long id);

    /**
     * 아직 모이기 전인 3교시 (무산 여부는 서비스에서 걸러낸다).
     * <ul>
     *   <li>federated=false: 우리 학교 게시판 — 이 학교 3교시 중 연합이 아닌 것. 다른 기수 전용은 빼고
     *       (그 학교 교수·대학원장은 allCohorts=true 로 모두 본다)</li>
     *   <li>federated=true: 연합 게시판 — 다섯 학교의 연합 3교시 전부</li>
     * </ul>
     */
    @Query("""
            select m from Meetup m join fetch m.host join fetch m.school
            where m.canceled = false and m.meetAt > :now and m.federated = :federated
              and (m.federated = true
                   or (m.school.id = :schoolId
                       and (:allCohorts = true or m.onlyCohort is null or m.onlyCohort = :cohort)))
            order by m.meetAt asc
            """)
    List<Meetup> findUpcoming(@Param("now") Instant now, @Param("federated") boolean federated,
            @Param("schoolId") Long schoolId, @Param("cohort") int cohort, @Param("allCohorts") boolean allCohorts,
            Pageable pageable);

    /**
     * 성사되어 실제로 모인 3교시 (게시판 구분은 findUpcoming 과 같다).
     * 최소 인원이 없는(무조건 진행) 3교시는 2명 이상 모였을 때만 친다.
     */
    @Query("""
            select m from Meetup m join fetch m.host join fetch m.school
            where m.canceled = false and m.meetAt <= :now and m.participantCount >= coalesce(m.minPeople, 2)
              and m.federated = :federated
              and (m.federated = true
                   or (m.school.id = :schoolId
                       and (:allCohorts = true or m.onlyCohort is null or m.onlyCohort = :cohort)))
            order by m.meetAt desc
            """)
    List<Meetup> findPastConfirmed(@Param("now") Instant now, @Param("federated") boolean federated,
            @Param("schoolId") Long schoolId, @Param("cohort") int cohort, @Param("allCohorts") boolean allCohorts,
            Pageable pageable);

    /** 관리 화면: 대학원장은 모든 학교 */
    @Query("select m from Meetup m join fetch m.host join fetch m.school order by m.meetAt desc")
    List<Meetup> findAllWithHost(Pageable pageable);

    /** 관리 화면: 교수는 자기 학교 3교시 (자기 학교 원우가 연 연합 3교시 포함) */
    @Query("""
            select m from Meetup m join fetch m.host join fetch m.school
            where m.school.id = :schoolId
            order by m.meetAt desc
            """)
    List<Meetup> findAllWithHost(@Param("schoolId") Long schoolId, Pageable pageable);

    /** 내가 연, 아직 열려 있는 3교시 (탈퇴할 때 취소한다) */
    @Query("select m from Meetup m where m.host.id = :hostId and m.canceled = false and m.meetAt > :now")
    List<Meetup> findOpenHostedBy(@Param("hostId") Long hostId, @Param("now") Instant now);

    long countBySchoolId(Long schoolId);

    /** 성사된(최소 인원이 모인) 3교시 수. 최소 인원이 없으면 2명 이상 모였을 때만 친다 */
    @Query("select count(m) from Meetup m where m.canceled = false and m.participantCount >= coalesce(m.minPeople, 2)")
    long countConfirmed();

    @Query("""
            select count(m) from Meetup m
            where m.school.id = :schoolId and m.canceled = false and m.participantCount >= coalesce(m.minPeople, 2)
            """)
    long countConfirmed(@Param("schoolId") Long schoolId);
}
