package com.anam3.school;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 가입할 수 있는 학교 (IT연합회: 고려대·연세대·성균관대·서강대·한양대).
 * 이 학교 이메일 도메인(SchoolEmailDomain)으로만 인증코드를 받을 수 있다.
 */
@Entity
@Table(name = "schools")
public class School {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 고려대학교 */
    @Column(nullable = false, length = 40)
    private String name;

    /** 화면 테마 색을 고르는 코드: KU, YONSEI, SKKU, SOGANG, HANYANG. 관리 화면에서 새로 넣은 학교는 null (기본 색) */
    @Column(length = 20, unique = true)
    private String code;

    /** 배지에 쓰는 짧은 이름: 고려대 */
    @Column(name = "short_name", nullable = false, length = 10)
    private String shortName;

    /** 화면에 보일 동네 이름 (안암 · 신촌 · 서강 · 혜화 · 왕십리): 로그인하면 "신촌3교시"처럼. 없으면 공식 이름 */
    @Column(length = 10)
    private String place;

    /** SW·AI융합대학원 */
    @Column(name = "graduate_school", length = 40)
    private String graduateSchool;

    /** 가입할 때 고를 수 있는 가장 최근 기수 (새 기수가 들어오면 대학원장이 올린다) */
    @Column(name = "latest_cohort", nullable = false)
    private int latestCohort;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected School() {
    }

    public School(String name, String shortName, String graduateSchool, int latestCohort, int sortOrder, Instant now) {
        this.name = name;
        this.shortName = shortName;
        this.graduateSchool = graduateSchool;
        this.latestCohort = latestCohort;
        this.sortOrder = sortOrder;
        this.createdAt = now;
    }

    /** 대학원장 전용 (SchoolService) */
    public void update(String name, String shortName, String graduateSchool, int latestCohort) {
        this.name = name;
        this.shortName = shortName;
        this.graduateSchool = graduateSchool;
        this.latestCohort = latestCohort;
    }

    public void changePlace(String place) {
        this.place = place;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public String getShortName() {
        return shortName;
    }

    public String getPlace() {
        return place;
    }

    public String getGraduateSchool() {
        return graduateSchool;
    }

    public int getLatestCohort() {
        return latestCohort;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
