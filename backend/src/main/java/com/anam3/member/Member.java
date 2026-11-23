package com.anam3.member;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "members")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, unique = true, length = 30)
    private String loginId;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /** 실명. 화면에는 규칙에 따라 마스킹되어 나간다 (MeetupMapper 참고) */
    @Column(nullable = false, length = 20)
    private String name;

    /** 기수. 61 -> "61기" */
    @Column(nullable = false)
    private int cohort;

    /** 학과 (운영자 계정 등 처음엔 비어 있을 수 있음) */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 30)
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

    protected Member() {
    }

    public Member(String loginId, String passwordHash, String name, int cohort, MemberRole role, Instant now) {
        this(loginId, passwordHash, name, cohort, null, role, now);
    }

    public Member(String loginId, String passwordHash, String name, int cohort, Department department,
            MemberRole role, Instant now) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.name = name;
        this.cohort = cohort;
        this.department = department;
        this.role = role;
        this.status = MemberStatus.ACTIVE;
        this.createdAt = now;
    }

    public String cohortLabel() {
        return cohort + "기";
    }

    /** 이름 옆에 붙는 짧은 학과 이름 (없으면 null) */
    public String departmentShortLabel() {
        return department == null ? null : department.shortLabel();
    }

    public String departmentLabel() {
        return department == null ? null : department.label();
    }

    public boolean isAdmin() {
        return role == MemberRole.ADMIN;
    }

    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
    }

    public void updateProfile(String name, int cohort, Department department) {
        this.name = name;
        this.cohort = cohort;
        this.department = department;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public void ban() {
        this.status = MemberStatus.BANNED;
    }

    public void unban() {
        this.status = MemberStatus.ACTIVE;
    }

    public void promoteToAdmin() {
        this.role = MemberRole.ADMIN;
    }

    public Long getId() {
        return id;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getPasswordHash() {
        return passwordHash;
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
}
