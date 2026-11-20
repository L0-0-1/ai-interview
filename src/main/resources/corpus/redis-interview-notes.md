# Redis 面试笔记

## Redis 如何在库存足够时才扣减？

假设 Redis 保存可售库存，每个请求都对同一个商品键执行扣减。校验库存不少于购买量和实际扣减应在同一个 Lua 脚本内完成。库存不足时直接返回失败，库存充足时扣减并返回成功。如果初始库存是 1、两个请求各买 1 件，其中一个脚本把库存减成 0，另一个脚本看到 0 后拒绝扣减。单独使用 `DECR` 会无条件减 1，库存为 0 时也能减到 -1。返回接口可用独立的成功标志，避免把扣完后剩余 0 件和购买失败混成同一个值。

出处：[Redis Lua 脚本](https://redis.io/docs/latest/develop/programmability/eval-intro/) · [DECR](https://redis.io/docs/latest/commands/decr/)

## Redis 的 WATCH、MULTI、EXEC 与 Lua 有什么区别？

`MULTI` 后的命令先排队，`EXEC` 再执行这组命令，执行期间其他客户端的命令无法插入。如果要根据当前值决定是否更新，可以先 `WATCH` 目标键，再读取并判断旧值，最后在事务里发出写入。如果被监视的键在 `EXEC` 前改变，本次事务会放弃执行，客户端可以重新读取和判断。Lua 则可以在 Redis 里用一个脚本连续执行读、判断和写。Redis 事务中某条命令的运行时错误不会把此前已执行的命令回滚，后续排队的命令仍会执行。

出处：[Redis 事务](https://redis.io/docs/latest/develop/using-commands/transactions/) · [WATCH](https://redis.io/docs/latest/commands/watch/) · [Redis Lua 脚本](https://redis.io/docs/latest/develop/programmability/eval-intro/)

## Redis 锁过期后，旧请求怎样安全解锁？

用一次 `SET` 通过 `NX` 和 `PX` 一起设置锁和值的过期时间。值使用这次抢锁独有的 token。解锁时用 Lua 在同一个原子操作里读取 token 并在相等时删除键。假设 A 的锁过期后 B 已经得到同一个锁，A 解锁时发现 token 已变化，就保留 B 的锁。如果把 `GET` 与 `DEL` 放在两次请求里，锁可能在两步之间换持有者，即使 `GET` 时验证通过也可能误删 B 的锁。

出处：[Redis 分布式锁](https://redis.io/docs/latest/develop/clients/patterns/distributed-locks/)

## 公共题详情的 Cache Aside 怎样写？

读取题目时先查 Redis 缓存。未命中就查 MySQL 并缓存结果，设置有效期。题目编辑成功后，在 MySQL 事务提交之后删除对应的缓存键。举例来说，把题目 6 改完并删缓存后，下一个未命中的请求会从 MySQL 取新题。数据库更新和删缓存是两次操作，删除失败时旧值可能继续被读到，可以通过删除重试与有效期缩短旧值停留时间。

出处：[Redis Cache Aside](https://redis.io/docs/latest/develop/use-cases/cache-aside/)

## 缓存击穿、穿透和雪崩怎样从请求现象区分？

- 击穿发生在同一个热门题目失去缓存时，大量请求同时去查这一题。可用按题目 ID 加锁合并并发回源，拿锁后再次查询缓存，避免等待者又执行一次 MySQL 查询。
- 穿透指查不存在的题目多次绕到 MySQL。对合法查询参数产生的真实缺失，可短时缓存一个明确的缺失标记；题目创建时清除对应的缺失标记。输入校验和限流可以减少无效请求。
- 雪崩是大量键集中失效或缓存服务不可用，跨多个键的回源一起暴涨。针对集中过期可分散到期时间，针对缓存故障可限制数据库回源并降级。验证时分别观测同一热门题、反复不存在的题和大量不同题的回源次数。

出处：[Redis Cache Aside](https://redis.io/docs/latest/develop/use-cases/cache-aside/)

## 用 SCAN 翻页时，某一页没有 key 就结束了吗？

从游标 0 开始调用，使用每次返回的新游标继续扫描，直到服务端再次返回游标 0。带 `MATCH` 时，中间某次结果可能为空，仍要按游标继续。 `COUNT` 给 Redis 一个大致工作量提示，结果条数会波动。完整遍历也可能重复返回某个 key，处理方需要允许重复或去重。要处理正在变化的数据时，遍历结果不能当成同一时刻的快照。

出处：[Redis SCAN](https://redis.io/docs/latest/commands/scan/)
