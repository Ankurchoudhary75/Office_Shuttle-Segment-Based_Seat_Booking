-- KEYS[1..S] = trip:{tripId}:leg:{i}:free bitmaps for legs Xs..Xd-1
-- ARGV[1] = seatBit (0-based)
local seatBit = tonumber(ARGV[1])
for i = 1, #KEYS do
    redis.call('SETBIT', KEYS[i], seatBit, 1)
end
return 1
