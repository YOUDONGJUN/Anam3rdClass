-- 직책: 원우(USER) / 교수(MANAGER) / 대학원장(OWNER, 1명)
-- 예전 관리자(ADMIN)는 대학원장이 된다. 대학원장이 여럿이면 서버가 시작할 때 OWNER_EMAIL 기준으로 1명만 남긴다
update members set role = 'OWNER' where role = 'ADMIN';
