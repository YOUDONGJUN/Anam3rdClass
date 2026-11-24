package com.anam3.push;

/**
 * 보낼 알림 한 건.
 *
 * @param path 알림을 누르면 앱이 여는 화면 (예: /meetups/12)
 * @param tag  같은 tag 의 알림은 알림창에서 새 것으로 바뀐다 (예: 같은 3교시의 참여 알림은 하나만)
 */
public record PushMessage(PushType type, String title, String body, String path, String tag) {
}
