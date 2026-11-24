-- 학교 이메일 + 비밀번호 로그인 (카카오 없이 가입, 또는 카카오 가입자가 비밀번호를 만든 경우). 예전 password_hash 와는 따로 둔다
alter table members add column email_password_hash varchar(100);

-- email_verifications.kakao_id 는 이제 "코드를 요청한 사람"이다: 카카오 회원번호, 또는 이메일로 시작한 가입 토큰의 임의 음수
