-- 최대 인원 "제한 없음" 지원: max_people 가 null 이면 정원 없이 계속 참여할 수 있다.
alter table meetups alter column max_people drop not null;
