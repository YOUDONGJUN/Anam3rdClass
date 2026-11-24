-- 3교시 옵션: 중간 합류 OK, 참여비(금액 · 선입금)
-- 중간 합류 OK: 늦게 와도 되고, 성사된 뒤에도 모임 시작 전까지 참여할 수 있다
alter table meetups add column late_join boolean not null default false;
-- 참여비(원). 비어 있으면 참여비 없음
alter table meetups add column fee integer;
-- 참여비를 미리 보내야 하는지
alter table meetups add column fee_prepaid boolean not null default false;
