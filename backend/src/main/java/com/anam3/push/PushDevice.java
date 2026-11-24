package com.anam3.push;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.anam3.member.Member;

/** 앱을 설치한 기기 하나 (Firebase 푸시 토큰). 같은 폰에서 다른 사람이 로그인하면 그 사람 것으로 바뀐다 */
@Entity
@Table(name = "push_devices")
public class PushDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = 512, unique = true)
    private String token;

    /** android | ios */
    @Column(nullable = false, length = 10)
    private String platform;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 앱이 마지막으로 토큰을 알려 준 때 (앱을 열 때마다) */
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected PushDevice() {
    }

    public PushDevice(Member member, String token, String platform, Instant now) {
        this.member = member;
        this.token = token;
        this.platform = platform;
        this.createdAt = now;
        this.lastSeenAt = now;
    }

    /** 다시 알려 옴: 로그인한 사람이 바뀌었으면 그 사람 것으로 */
    public void seen(Member member, String platform, Instant now) {
        this.member = member;
        this.platform = platform;
        this.lastSeenAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getToken() {
        return token;
    }
}
