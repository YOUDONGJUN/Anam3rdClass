package com.anam3.push;

/** 알림 보내기를 요청 스레드 밖에서 돌린다 (운영: AsyncPushDispatcher, 테스트: 바로 실행) */
public interface PushDispatcher {

    void dispatch(Runnable task);
}
