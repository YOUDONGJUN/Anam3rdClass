package com.anam3.school;

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

/**
 * 학교별 학과(전공). 대학원장이 관리 화면에서 추가·이름 바꾸기·숨기기를 한다.
 * 숨긴 학과는 가입할 때 고를 수 없지만, 이미 그 학과인 원우에게는 그대로 보인다.
 */
@Entity
@Table(name = "departments")
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    /** 인공지능융합학과 */
    @Column(nullable = false, length = 40)
    private String name;

    /** 이름 옆에 붙는 짧은 이름: 인융 */
    @Column(name = "short_name", nullable = false, length = 10)
    private String shortName;

    /** 학교의 대학원과 다를 때만 적는다 (예: 연세대 공학대학원). 비우면 학교의 대학원 */
    @Column(name = "graduate_school", length = 40)
    private String graduateSchool;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Department() {
    }

    public Department(School school, String name, String shortName, String graduateSchool, int sortOrder,
            Instant now) {
        this.school = school;
        this.name = name;
        this.shortName = shortName;
        this.graduateSchool = graduateSchool;
        this.sortOrder = sortOrder;
        this.active = true;
        this.createdAt = now;
    }

    /** 대학원장 전용 (SchoolService) */
    public void update(String name, String shortName, String graduateSchool, boolean active) {
        this.name = name;
        this.shortName = shortName;
        this.graduateSchool = graduateSchool;
        this.active = active;
    }

    /** 이 학과가 속한 대학원 이름 (학과에 따로 없으면 학교의 대학원) */
    public String graduateSchoolName() {
        return graduateSchool != null ? graduateSchool : school.getGraduateSchool();
    }

    public boolean belongsTo(School other) {
        return school.getId().equals(other.getId());
    }

    public Long getId() {
        return id;
    }

    public School getSchool() {
        return school;
    }

    public String getName() {
        return name;
    }

    public String getShortName() {
        return shortName;
    }

    public String getGraduateSchool() {
        return graduateSchool;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
