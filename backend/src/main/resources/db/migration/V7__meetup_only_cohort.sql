-- 우리 기수만 모집하는 3교시: 열 때 개설자의 기수를 적어 둔다 (null 이면 누구나)
-- 다른 기수에게는 목록에 보이지 않고 참여도 막힌다
alter table meetups add column only_cohort integer;
