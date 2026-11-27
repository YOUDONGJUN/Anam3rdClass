-- 톡방: 이모티콘 메시지, "○○님이 들어왔어요 / 나갔어요" 안내 줄
-- kind: TEXT(원우가 보낸 메시지) · JOIN(들어옴) · LEAVE(나감). 안내 줄의 member_id 는 들어오고 나간 사람
alter table chat_messages add column kind varchar(10) default 'TEXT' not null;
-- 이모티콘 이름 (ChatStickers). 이모티콘 메시지는 글·사진 없이 이것만 있다
alter table chat_messages add column sticker varchar(40);
