-- 모임 1시간 전 알림을 보낸 시각 (모임 시간을 바꾸면 다시 비운다 → 새 시간 1시간 전에 또 알린다)
alter table meetups add column meet_reminded_at timestamp with time zone;

-- 밤(한국 시간 0~8시)에 열려서 새 3교시 알림을 아침 8시로 미뤘는지
alter table meetups add column announce_pending boolean not null default false;

-- 밤에 올려서 새 공지 알림을 아침 8시로 미뤘는지
alter table notices add column announce_pending boolean not null default false;
