-- KEYS[1..S] = trip:{tripId}:leg:{i}:free bitmaps for legs Xs..Xd-1
-- ARGV[1] = seatCount
local seatCount = tonumber(ARGV[1])
local tmpKey = 'tmp:result:' .. KEYS[1]

if #KEYS == 1 then
    local seatBit = redis.call('BITPOS', KEYS[1], 1)
    if seatBit == -1 or seatBit >= seatCount then
        return -1
    end
    redis.call('SETBIT', KEYS[1], seatBit, 0)
    return seatBit + 1
end

redis.call('BITOP', 'AND', tmpKey, unpack(KEYS))
local seatBit = redis.call('BITPOS', tmpKey, 1)
redis.call('DEL', tmpKey)

if seatBit == -1 or seatBit >= seatCount then
    return -1
end

for i = 1, #KEYS do
    redis.call('SETBIT', KEYS[i], seatBit, 0)
end

return seatBit + 1
