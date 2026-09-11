-- KEYS[1] = login:fail:{username}      실패 기록 (Sorted Set)
-- KEYS[2] = login:blocked:{username}   차단 중복 방지
-- ARGV[1] = 현재 시각(ms)
-- ARGV[2] = 윈도우 시작 시각(ms)
-- ARGV[3] = member (login_attempts.id)
-- ARGV[4] = 키 TTL(초)
-- ARGV[5] = 임계치

redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[2])
redis.call('ZADD', KEYS[1], ARGV[1], ARGV[3])
redis.call('EXPIRE', KEYS[1], ARGV[4])

local count = redis.call('ZCARD', KEYS[1])
if count < tonumber(ARGV[5]) then
  return {count, 0}
end

-- 임계치에 도달한 요청이 여럿이어도 처음 하나만 1 을 받는다
if redis.call('SET', KEYS[2], '1', 'NX', 'EX', ARGV[4]) then
  return {count, 1}
end
return {count, 0}
