-- 약관 버전: 동의한 약관 버전을 남긴다. 서버의 Member.CURRENT_TERMS_VERSION 보다 낮으면 다시 동의 화면을 본다.
-- 이미 동의한 회원은 1(지금 약관), 아직 동의하지 않은 예전 회원은 0
alter table members add column terms_version integer not null default 0;

update members set terms_version = 1 where terms_agreed_at is not null;
