package com.anam3.school;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 학교 이메일 도메인. 학교마다 여러 개 둘 수 있다 (성균관대: g.skku.edu, skku.edu) */
@Entity
@Table(name = "school_email_domains")
public class SchoolEmailDomain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    /** 소문자. 하위 도메인도 같은 학교로 본다 (skku.edu 면 g.skku.edu 도) */
    @Column(nullable = false, unique = true, length = 60)
    private String domain;

    protected SchoolEmailDomain() {
    }

    public SchoolEmailDomain(School school, String domain) {
        this.school = school;
        this.domain = domain;
    }

    /** 이메일 도메인(g.skku.edu)이 이 도메인(skku.edu)이거나 그 하위 도메인인지 */
    public boolean matches(String emailDomain) {
        return emailDomain.equals(domain) || emailDomain.endsWith("." + domain);
    }

    public Long getId() {
        return id;
    }

    public School getSchool() {
        return school;
    }

    public String getDomain() {
        return domain;
    }
}
