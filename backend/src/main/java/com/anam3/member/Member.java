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

    /** 입학 연도. 2024 -> "24학번" */
    @Column(name = "admission_year", nullable = false)
    private int admissionYear;

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

    public Member(String loginId, String passwordHash, String name, int admissionYear, MemberRole role, Instant now) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.name = name;
        this.admissionYear = admissionYear;
        this.role = role;
        this.status = MemberStatus.ACTIVE;
        this.createdAt = now;
    }

    public String admissionLabel() {
        return String.format("%02d학번", admissionYear % 100);
    }

    public boolean isAdmin() {
        return role == MemberRole.ADMIN;
    }

    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
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

    public int getAdmissionYear() {
        return admissionYear;
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
