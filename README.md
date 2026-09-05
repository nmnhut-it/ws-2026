# Workshop 1 — Đánh boss và lưu trữ

Server này có sẵn toàn bộ Workshop 0. Hôm nay hai phần:

| Phần | Bạn làm | File |
|---|---|---|
| **1 — Đánh boss** | đủ người mới triệu hồi; mỗi request `attack` trừ máu boss; báo tất cả | `GameServer.java` (TODO 1, 3), `BeastState.java` (TODO 2) |
| **2 — Hàng đợi + database** | mọi đòn qua **một** hàng đợi, **một** worker; lưu kỷ lục vào Redis | `FightQueue.java` (TODO 4), `GameServer.java` (TODO 5), `Records.java` (TODO 6) |

Mã gian lận cho client: `sieuthu` (đánh boss), `kyluc` (bảng kỷ lục). Sẽ tiết lộ khi đến lúc.

## 0. Lấy code

```
git clone -b ws-1-exercise https://github.com/nmnhut-it/ws-2026.git
```

Nhánh `ws-1-answer` là lời giải, mở sau buổi. Không có git thì tải `w1.zip` trên ws-2026-server.zingplay.dev.

## 1. Chạy

Như Workshop 0: **`run.bat`** (Mac/Linux `./run.sh`), client: ô Máy chủ `localhost:9000` → **Connect**. Ngưỡng triệu hồi là **40**
(trang chấm bài mở 45 kết nối). Thử một mình: sửa tạm `-Dbeast.threshold=40` thành `3` trong `run.bat`,
nhớ đổi lại trước khi chấm.

Redis: chạy `RedisSimulator` của dự án demo (`demo-scripts.zip` → `redis.bat`) hoặc Redis thật ở
`localhost:6379`. Không có Redis thì server tự rơi về file `records.txt` — vẫn chạy được.

## 2. Phần 1 — Đánh boss

| TODO | Ở đâu | Làm gì |
|---|---|---|
| 1 | `GameServer.checkBeast()` | `GameLogic.conjure(registry.count())` sau mỗi `call`; có boss → `fight.spawn(b)`, gửi `beast` cho tất cả, **một lần** |
| 2 | `BeastState` | **tự thêm cấu trúc dữ liệu** (đề xuất: `Map` đòn thứ mấy theo uid, `Map` bảng công, `Map` tên, `slainBy`) rồi viết `strike()`, `board()`, `slainByUid()` theo 7 bước ghi trên hàm |
| 3 | `GameServer.handleAttack()` + `doStrike()` | `registry.get(client)` (không tin uid client gửi) → `fight.strike` → `sendAll(beast{lastHit})`; `killed` → `slain` |

Viết **thẳng tay**, đừng nghĩ tới nhiều luồng. `test.bat`: test 1, 2 xanh là xong phần 1.
Test 3 (20 luồng cùng đánh) chỉ để **xem**: nó sẽ báo mất đòn hoặc `ConcurrentModificationException`
— đúng dự định. Cả lớp nối vào server của một nhóm và đánh: đôi khi **hai người cùng kết liễu**.

## 3. Phần 2 — Hàng đợi và database

| TODO | Ở đâu | Làm gì |
|---|---|---|
| 4 | `FightQueue` | thêm một `BlockingQueue<Registry.Player>`; `submit()` bỏ vào hàng rồi return; `run()` là vòng lặp của **một** worker: `take()` → `handler.handle()`; `pending()` = size |
| 5 | `GameServer.handleAttack()` | đổi `doStrike(s)` thành `queue.submit(s)` — luồng kết nối không chạm vào `BeastState` nữa |
| 6 | `Records` | `saveBest(name, total)`: `zscore` rồi `zadd` nếu cao hơn; `top(n)`: `zrevrange` → `List<Entry>` |

`Db` (cho sẵn) nói giao thức Redis bằng Java thuần: `zadd / zscore / zrevrange`. `saveRecords()` được
gọi sau `slain`; `{"cmd":"records"}` trả top từ database — tắt server bật lại vẫn còn.

Test 4 (16 luồng submit, đúng một người kết liễu) và test 5 (kỷ lục, mở lại database vẫn còn) xanh là
xong phần 2. Chấm bài: 3 mức — triệu hồi + đánh, đúng một người kết liễu (~480 đòn), kỷ lục.

## 4. Client — không sửa gì

Client giống Workshop 0. Đánh boss = gõ `{"cmd":"attack"}` rồi Enter; **giữ Enter** để đánh liên tục.
Mã `sieuthu` chỉ mở bảng vẽ thanh máu và bảng công từ gói `beast` / `slain` bạn thấy trong log.

## Có gì trong thư mục này

```
run.bat / run.sh          biên dịch + chạy (ngưỡng 40)
test.bat / test.sh        Tests.java — 5 test
client/index.html         client kiểu Postman — giống Workshop 0
src/GameServer.java       TODO 1, 3, 5
src/BeastState.java       TODO 2 — cấu trúc dữ liệu của bạn + strike()
src/FightQueue.java       TODO 4 — hàng đợi + worker
src/Records.java          TODO 6 — kỷ lục
src/Db.java               Redis (RESP) hoặc file — cho sẵn
src/Registry.java, Net.java, Frame.java   cho sẵn
lib/gamelogic.jar         conjure() + rollDamage(uid, lầnĐánh) — 10..60, thuần tuý
Dockerfile, .gitignore    tự deploy server của nhóm (tuỳ chọn)
tools/Run.java, build.gradle.kts   Gradle / IntelliJ (tuỳ chọn)
```

## Hay gặp

| Triệu chứng | Nguyên nhân |
|---|---|
| chấm bài báo *boss không xuất hiện* | chạy bằng `run.bat` (ngưỡng 40) và TODO 1 |
| `ConcurrentModificationException` trong console | phần 1: nhiều luồng cùng sửa `HashMap` — phần 2 sửa bằng hàng đợi |
| hai tin `slain` | đòn chưa đi qua `FightQueue`, hoặc kiểm tra "về 0 & chưa ai" nằm ngoài `strike()` |
| `records` rỗng | `Records.top()` chưa làm, hoặc chưa có trận nào kết thúc |
| `[db] khong co Redis` | bình thường: dùng file `records.txt`; muốn Redis thì chạy `redis.bat` của demo-scripts |
