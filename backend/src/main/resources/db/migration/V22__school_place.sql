-- 학교마다 화면에 보일 동네 이름: 로그인하면 "신촌3교시"처럼 바뀐다 (공식 서비스 이름은 안암3교시)
alter table schools add column place varchar(10);

update schools set place = '안암' where code = 'KU';
update schools set place = '신촌' where code = 'YONSEI';
update schools set place = '혜화' where code = 'SKKU';
update schools set place = '서강' where code = 'SOGANG';
update schools set place = '왕십리' where code = 'HANYANG';
