package com.anam3.push;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.anam3.auth.LoginMember;
import com.anam3.push.NotificationService.NotificationList;
import com.anam3.push.NotificationService.UnreadCount;

/** 알림함 (웹·앱 공통) */
@RestController
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    /** 최근 알림 50개와 안 읽은 수 */
    @GetMapping("/api/me/notifications")
    public NotificationList list(@AuthenticationPrincipal LoginMember me) {
        return notifications.list(me);
    }

    /** 종 아이콘 숫자 (1분마다 부른다) */
    @GetMapping("/api/me/notifications/unread-count")
    public UnreadCount unreadCount(@AuthenticationPrincipal LoginMember me) {
        return notifications.unreadCount(me);
    }

    /** 알림함을 열어 봤다: 모두 읽음 */
    @PostMapping("/api/me/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(@AuthenticationPrincipal LoginMember me) {
        notifications.readAll(me);
    }
}
