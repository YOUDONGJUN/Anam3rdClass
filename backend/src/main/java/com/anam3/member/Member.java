package com.anam3.member;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.anam3.push.PushType;
import com.anam3.school.Department;
import com.anam3.school.School;

/**
 * 회원(원우). 카카오 회원번호로 로그인하고, 가입할 때 한 번 학교 이메일을 인증한다.
 * 이름은 가입한 뒤 본인이 바꿀 수 없다 (교수·대학원장이 관리 화면에서 고친다. 대학원장은 본인 이름도 고친다).
 * 학과·기수는 내 정보에서 고친다 — 원우는 30일에 한 번, 교수·대학원장은 제한 없이 (AccountService).
 * 탈퇴하면 이름·이메일·카카오 연결을 지우고 "탈퇴한 원우"로만 남는다 (3교시 기록의 짝을 맞추려고 행은 남긴다).
 */
@Entity
@Table(name = "members")
public class Member {

    public static final String WITHDRAWN_NAME = "탈퇴한 원우";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 카카오 회원번호. 예전(아이디·비밀번호) 회원은 처음 카카오로 로그인하기 전까지 null */
    @Column(name = "kakao_id", unique = true)
    private Long kakaoId;

    /** 인증된 학교 이메일 (소문자). 카카오 계정이 바뀌었을 때 계정을 되찾는 열쇠이기도 하다 */
    @Column(length = 120, unique = true)
    private String email;

    /** 인증한 학교 이메일의 학교 (예전 회원은 고려대) */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    /** 실명. 화면에는 규칙에 따라 마스킹되어 나간다 (MeetupMapper 참고) */
    @Column(nullable = false, length = 20)
    private String name;

    /** 기수. 61 -> "61기" */
    @Column(nullable = false)
    private int cohort;

    /** 학과 (예전 운영자 계정 등은 비어 있을 수 있음). 늘 이 회원 학교의 학과다 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private MemberStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 이용약관·개인정보 처리방침에 동의한 시각. 예전 회원은 null → 다음 로그인 때 동의 화면 */
    @Column(name = "terms_agreed_at")
    private Instant termsAgreedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    /**
     * 예전(아이디·비밀번호 시절) 로그인 정보. 카카오로 처음 들어온 예전 회원이 이 아이디·비밀번호로 본인임을 확인하면
     * 예전 계정에 카카오·학교 이메일을 연결하고 지운다. 새로 가입한 회원은 늘 null
     */
    @Column(name = "login_id", length = 30)
    private String loginId;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    /**
     * 이메일 로그인 비밀번호 (BCrypt). 학교 이메일 + 이 비밀번호로 로그인한다.
     * 카카오 없이 가입했거나, 카카오 가입자가 학교 이메일 인증 뒤 비밀번호를 만들었을 때만 있다. 위의 예전 비밀번호와는 다르다
     */
    @Column(name = "email_password_hash", length = 100)
    private String emailPasswordHash;

    /** 끈 앱 푸시 알림 종류 (쉼표로 구분, 비어 있으면 모두 받는다) */
    @Column(name = "push_off", length = 100)
    private String pushOff;

    /**
     * 로그인 토큰 버전. 비밀번호를 정하거나 바꾸면 1 올라가서, 그 전에 받은 로그인 토큰(다른 기기)은 더는 안 된다.
     * 토큰에 버전이 없으면(예전 토큰) 0 으로 본다
     */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    /** 내 정보에서 학과·기수를 마지막으로 고친 시각 (원우는 30일에 한 번). 관리 화면에서 고치면 바뀌지 않는다 */
    @Column(name = "profile_changed_at")
    private Instant profileChangedAt;

    /** 랭킹에 안 나오기 */
    @Column(name = "ranking_hidden", nullable = false)
    private boolean rankingHidden;

    /** 송금 링크: 토스 아이디 (정산할 때 함께한 원우에게만 보인다) */
    @Column(name = "toss_id", length = 30)
    private String tossId;

    /** 송금 링크: 카카오페이 송금 링크 (https://qr.kakaopay.com/…) */
    @Column(name = "kakaopay_url", length = 100)
    private String kakaoPayUrl;

    protected Member() {
    }

    public Member(Long kakaoId, String email, School school, String name, int cohort, Department department,
            MemberRole role, Instant now) {
        this.kakaoId = kakaoId;
        this.email = email;
        this.school = school;
        this.name = name;
        this.cohort = cohort;
        this.department = department;
        this.role = role;
        this.status = MemberStatus.ACTIVE;
        this.createdAt = now;
    }

    /** 예전(아이디·비밀번호 시절) 회원 — 로컬 샘플·테스트용. 운영에는 이미 DB 에 있다 */
    public static Member legacy(String loginId, String passwordHash, School school, String name, int cohort,
            Department department, Instant now) {
        Member m = new Member(null, null, school, name, cohort, department, MemberRole.USER, now);
        m.loginId = loginId;
        m.passwordHash = passwordHash;
        return m;
    }

    public String cohortLabel() {
        return cohort + "기";
    }

    /** 이름 옆에 붙는 짧은 학과 이름 (없으면 null) */
    public String departmentShortLabel() {
        return department == null ? null : department.getShortName();
    }

    public String departmentLabel() {
        return department == null ? null : department.getName();
    }

    public boolean isWithdrawn() {
        return status == MemberStatus.WITHDRAWN;
    }

    /** 아직 카카오로 이어지지 않은 예전 회원 (예전 아이디·비밀번호로 이을 수 있다) */
    public boolean isUnclaimedLegacy() {
        return kakaoId == null && email == null && passwordHash != null && !isWithdrawn();
    }

    public String getLegacyPasswordHash() {
        return passwordHash;
    }

    /** 카카오로 이은 뒤에는 예전 아이디·비밀번호를 쓰지 않는다 */
    public void clearLegacyLogin() {
        this.loginId = null;
        this.passwordHash = null;
    }

    public boolean hasAgreedTerms() {
        return termsAgreedAt != null;
    }

    public boolean isSameSchool(Long schoolId) {
        return school.getId().equals(schoolId);
    }

    public boolean isOwner() {
        return role == MemberRole.OWNER;
    }

    public boolean isStaff() {
        return role.isStaff();
    }

    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
    }

    /** 관리 화면에서 고친다 (AdminService). 본인이 고칠 때는 changeOwnProfile */
    public void updateProfile(String name, int cohort, Department department) {
        this.name = name;
        this.cohort = cohort;
        this.department = department;
    }

    /** 내 정보에서 본인이 고친다 (이름은 대학원장만 바뀐다 — AccountService 가 먼저 본다). 고친 시각을 남긴다 */
    /** startsLimit: 학과·기수가 바뀌면 30일 제한을 시작할지 (가입한 날 고친 것은 시작하지 않는다) */
    public void changeOwnProfile(String name, int cohort, Department department, Instant now, boolean startsLimit) {
        boolean affiliationChanged = this.cohort != cohort || !sameDepartment(department);
        this.name = name;
        this.cohort = cohort;
        this.department = department;
        if (affiliationChanged && startsLimit) {
            this.profileChangedAt = now;
        }
    }

    /** 이 학과와 지금 학과가 같은지 (둘 다 비어 있어도 같다) */
    public boolean sameDepartment(Department other) {
        if (department == null || other == null) {
            return department == other;
        }
        return department.getId().equals(other.getId());
    }

    public Instant getProfileChangedAt() {
        return profileChangedAt;
    }

    public boolean isRankingHidden() {
        return rankingHidden;
    }

    public void changeRankingHidden(boolean hidden) {
        this.rankingHidden = hidden;
    }

    public String getTossId() {
        return tossId;
    }

    public String getKakaoPayUrl() {
        return kakaoPayUrl;
    }

    /** 송금 링크 바꾸기 (PayLinks 로 다듬고 확인한 값) */
    public void changePayLinks(String tossId, String kakaoPayUrl) {
        this.tossId = tossId;
        this.kakaoPayUrl = kakaoPayUrl;
    }

    public void agreeTerms(Instant now) {
        this.termsAgreedAt = now;
    }

    /**
     * 탈퇴: 이름·이메일·카카오 연결·이메일 비밀번호·학과를 지운다. 다시 가입하면 새 계정이 된다.
     * 3교시·공지에는 "탈퇴한 원우"로 남는다.
     */
    public void withdraw(Instant now) {
        this.name = WITHDRAWN_NAME;
        this.kakaoId = null;
        this.email = null;
        this.emailPasswordHash = null;
        this.department = null;
        this.tossId = null;
        this.kakaoPayUrl = null;
        this.role = MemberRole.USER;
        this.status = MemberStatus.WITHDRAWN;
        this.withdrawnAt = now;
    }

    /**
     * 학교 이메일 인증을 마친 뒤 이메일 로그인 비밀번호를 정하거나 바꾼다 (BCrypt 로 바꾼 값).
     * 바꿀 때는 다른 기기의 로그인을 푼다 (토큰 버전 +1). 처음 만들 때는 그대로 둔다 — 카카오로 쓰던 폰 앱이 풀리지 않게
     */
    public void setEmailPassword(String hash) {
        boolean changing = this.emailPasswordHash != null;
        this.emailPasswordHash = hash;
        if (changing) {
            this.tokenVersion++;
        }
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public Set<PushType> getPushOff() {
        return PushType.parseList(pushOff);
    }

    public void changePushOff(Collection<PushType> off) {
        this.pushOff = PushType.joinList(off);
    }

    public boolean hasEmailPassword() {
        return emailPasswordHash != null;
    }

    public String getEmailPasswordHash() {
        return emailPasswordHash;
    }

    public boolean isKakaoLinked() {
        return kakaoId != null;
    }

    /** 학교 이메일 인증을 마친 카카오 계정을 이 회원에 연결한다 (예전 회원 이전, 카카오 계정 변경) */
    public void linkKakao(Long kakaoId) {
        this.kakaoId = kakaoId;
    }

    /** 예전 회원에게 대학원장이 학교 이메일을 미리 넣어 둔다. 그 이메일로 인증하면 이 계정으로 이어진다 */
    public void assignEmail(String email, School school) {
        this.email = email;
        this.school = school;
        if (department != null && !department.belongsTo(school)) {
            this.department = null; // 다른 학교로 옮기면 학과는 다시 넣는다
        }
    }

    public void changeRole(MemberRole role) {
        this.role = role;
    }

    /** 차단하면 교수 직책도 내려놓는다 */
    public void ban() {
        this.status = MemberStatus.BANNED;
        if (role == MemberRole.MANAGER) {
            this.role = MemberRole.USER;
        }
    }

    public void unban() {
        this.status = MemberStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public Long getKakaoId() {
        return kakaoId;
    }

    public String getEmail() {
        return email;
    }

    public School getSchool() {
        return school;
    }

    public String getName() {
        return name;
    }

    public int getCohort() {
        return cohort;
    }

    public Department getDepartment() {
        return department;
    }

    public MemberRole getRole() {
        return role;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getTermsAgreedAt() {
        return termsAgreedAt;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
    }
}
