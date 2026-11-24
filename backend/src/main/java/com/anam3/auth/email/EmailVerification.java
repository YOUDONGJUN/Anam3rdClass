package com.anam3.auth.email;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 학교 이메일로 보낸 인증코드 한 건. 코드는 해시로만 저장한다 (VerificationCodes).
 * 이 인증을 요청한 카카오 회원번호와 묶여 있어서, 다른 사람이 가로채 쓸 수 없다.
 */
@Entity
@Table(name = "email_verifications")
public class EmailVerification {

    /** 이만큼 틀리면 코드를 버리고 새로 받아야 한다 */
    public static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String email;

    /**
     * 코드를 요청한 사람 (요청 횟수를 센다): 카카오로 시작했으면 카카오 회원번호, 이메일로 시작했으면 가입 토큰의 임의 음수.
     * 이메일 로그인이 생기기 전 이름 그대로 kakao_id 열에 둔다
     */
    @Column(name = "kakao_id", nullable = false)
    private Long requesterId;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EmailVerification() {
    }

    public EmailVerification(String email, long requesterId, String codeHash, Instant expiresAt, Instant now) {
        this.email = email;
        this.requesterId = requesterId;
        this.codeHash = codeHash;
        this.attempts = 0;
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    /** 아직 쓰지 않았고, 만료 전이고, 기회가 남아 있으면 true */
    public boolean isUsableAt(Instant now) {
        return verifiedAt == null && now.isBefore(expiresAt) && attempts < MAX_ATTEMPTS;
    }

    public void failAttempt() {
        this.attempts++;
    }

    public int remainingAttempts() {
        return Math.max(0, MAX_ATTEMPTS - attempts);
    }

    public void markVerified(Instant now) {
        this.verifiedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public Long getRequesterId() {
        return requesterId;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
