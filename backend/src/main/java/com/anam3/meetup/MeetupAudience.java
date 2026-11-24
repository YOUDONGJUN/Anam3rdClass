package com.anam3.meetup;

/** 3교시를 누구에게 여는지. 열 때만 정하고 바꿀 수 없다 */
public enum MeetupAudience {
    /** 우리 학교 원우 누구나 (기본) */
    SCHOOL,
    /** 우리 학교 + 같은 기수만 */
    COHORT,
    /** 연합: IT연합회 다섯 학교 누구나 */
    ALL
}
