-- 3교시 장소의 위치: 장소 검색(카카오 로컬)에서 고르면 좌표와 지역(시·구)을 함께 저장한다.
-- 직접 쓴 장소는 비어 있다 (가까운 순에서 맨 뒤)
alter table meetups add column lat double precision;
alter table meetups add column lng double precision;
alter table meetups add column region varchar(30);

-- 학교 캠퍼스 위치: 내 위치를 쓰지 않을 때 "우리 학교에서 가까운 순"의 기준, 장소 검색에서 학교 근처 먼저
alter table schools add column lat double precision;
alter table schools add column lng double precision;

update schools set lat = 37.5894, lng = 127.0322 where code = 'KU';
update schools set lat = 37.5658, lng = 126.9386 where code = 'YONSEI';
update schools set lat = 37.5880, lng = 126.9930 where code = 'SKKU';
update schools set lat = 37.5509, lng = 126.9410 where code = 'SOGANG';
update schools set lat = 37.5572, lng = 127.0459 where code = 'HANYANG';
