package com.anam3.safety;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.anam3.auth.LoginMember;
import com.anam3.chat.ChatMessage;
import com.anam3.chat.ChatMessageRepository;
import com.anam3.meetup.ParticipationRepository;

/**
 * 톡방 메시지로 신고·차단할 때: 메시지 번호로 그 3교시와 보낸 사람을 찾는다.
 * 보낸 원우가 그 뒤에 3교시에서 나갔어도 그 사람이 대상이다 (예전에는 3교시 = 연 사람 신고로 들어갔다).
 * 그 톡방에 지금 들어갈 수 있는 원우만 (톡방을 볼 수 있는 사람과 같다)
 */
@Component
public class ChatMessageTargets {

    private static final DateTimeFormatter SENT_AT = DateTimeFormatter.ofPattern("M/d HH:mm")
            .withZone(ZoneId.of("Asia/Seoul"));

    private final ChatMessageRepository messages;
    private final ParticipationRepository participations;

    public ChatMessageTargets(ChatMessageRepository messages, ParticipationRepository participations) {
        this.messages = messages;
        this.participations = participations;
    }

    public Optional<ChatMessage> find(LoginMember viewer, Long messageId) {
        return messages.findWithMeetup(List.of(messageId)).stream().findFirst()
                .filter(m -> participations.existsByMeetupIdAndMemberId(m.getMeetup().getId(), viewer.id()));
    }

    /** 신고 기록에 남길 제목 ("톡방 메시지 · 11/27 21:05") */
    public static String title(ChatMessage m) {
        return "톡방 메시지 · " + SENT_AT.format(m.getCreatedAt());
    }

    /** 신고 기록에 남길 내용: 글 + 사진 수. 신고 전에 지웠으면 그렇다고 남긴다 */
    public static String body(ChatMessage m) {
        if (m.isDeleted()) {
            return "(신고하기 전에 보낸 사람이 지운 메시지예요)";
        }
        String text = m.getBody() == null ? "" : m.getBody().strip();
        int photos = m.getPhotos().size();
        String photoLine = photos == 0 ? "" : "[사진 " + photos + "장]";
        if (text.isEmpty()) {
            return photoLine;
        }
        return photoLine.isEmpty() ? text : text + "\n" + photoLine;
    }
}
