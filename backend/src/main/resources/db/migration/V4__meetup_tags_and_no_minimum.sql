-- 1) 마실 것(종류)을 여러 개 고를 수 있게: category 한 칸 → categories ("SOJU,BEER" 처럼 쉼표로 이어 저장)
-- 2) 모임 목적 태그: purposes ("친목,학업" 처럼 쉼표로 이어 저장, 없으면 null)
-- 3) 최소 인원 없음(= 몇 명이 오든 진행): min_people 이 null 이면 열자마자 성사
-- 4) 처음 배포 때 기본값 "운영자"로 만들어진 관리자 계정의 이름을 실명으로 바꾼다

alter table meetups add column categories varchar(100);
update meetups set categories = category;
alter table meetups alter column categories set not null;
alter table meetups drop column category;

alter table meetups add column purposes varchar(100);

alter table meetups alter column min_people drop not null;

update members set name = '유동준' where role = 'ADMIN' and name = '운영자';
