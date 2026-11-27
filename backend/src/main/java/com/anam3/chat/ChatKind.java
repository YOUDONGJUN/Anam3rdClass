package com.anam3.chat;

/** 톡방 메시지 종류: 원우가 보낸 메시지, 또는 누가 들어오고 나갔다는 안내 줄 */
public enum ChatKind {
    /** 글 · 사진 · 이모티콘 */
    TEXT,
    /** "○○님이 들어왔어요" — member 가 참여했다 */
    JOIN,
    /** "○○님이 나갔어요" — member 가 나갔거나 내보내졌다 (내보낸 것은 드러내지 않는다) */
    LEAVE
}
