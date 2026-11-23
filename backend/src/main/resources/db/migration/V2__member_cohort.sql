-- 학번(입학 연도) 대신 기수로 표시하고, 학과를 추가한다. 예: 김*수 · 61기 · 인융
-- 이미 가입한 회원은 61기로 채운다 (배포 초기에는 운영자 계정만 있었음). 학과는 각자 "내 정보"에서 고른다.

alter table members add column cohort integer;
update members set cohort = 61;
alter table members alter column cohort set not null;
alter table members drop column admission_year;

alter table members add column department varchar(30);
