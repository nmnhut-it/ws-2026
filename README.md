# Workshop 0 — Client gọi server

Bạn có **một client** (một file HTML) và **một server** (Java thuần, boilerplate chạy được).
Buổi hôm nay:

| Phần | Bạn làm |
|---|---|
| 0.0 Điểm danh | gửi cho ws-2026-server.zingplay.dev **một gói tin JSON** có tên + nhóm |
| 0.1 Server của nhóm | `GameServer.java`: trả lời `welcome`, báo `error` khi gói tin sai |
| Deploy | nhóm được chọn push code lên git, chạy code đó trên ws-2026-server.zingplay.dev, cả lớp nối vào |

Không Gradle, không Maven, không tải thư viện. Chỉ cần JDK.

---

## Lấy code

```
git clone -b ws-0-exercise https://github.com/nmnhut-it/ws-2026.git
```

Nhánh: `ws-0-exercise` (bài hôm nay) · `ws-0-answer` (lời giải, mở sau buổi) · `ws-1-exercise` · `ws-1-answer`.
Không có git thì tải `w0.zip` trên ws-2026-server.zingplay.dev — nội dung y hệt.

## 0. Kiểm tra máy (làm trước khi đến lớp)

Mở **Terminal** (Windows: gõ `cmd` vào ô tìm kiếm) và chạy:

```
javac -version
```

Phải hiện `javac 17` trở lên (`javac 21.0.x` là tốt). Nếu báo *not recognized* / *command not found*
thì cài JDK: <https://adoptium.net> → Latest LTS → cài mặc định → **mở lại terminal** rồi gõ lại.

## 1. Điểm danh — gửi một gói tin cho ws-2026-server.zingplay.dev

1. Mở client (địa chỉ trên bảng, hoặc nhấp đúp **`client/index.html`**). Client giống Postman:
   **một ô gõ JSON**, nút **Gửi** và **log**. Không có nút nào gửi hộ bạn — mọi gói tin đều do bạn gõ.
2. Ô **Máy chủ** = địa chỉ ws-2026-server.zingplay.dev (trên bảng) → **Connect**.
3. F12 → **Network** → **WS** → dòng `ws` → **Messages**. Để tab đó mở.
4. Gõ `{"cmd":"call"}` → Enter → server trả `error`: thiếu tên.
5. Gõ lại `{"cmd":"call","name":"Tên bạn","group":"Tên nhóm"}` → Enter.
   Server trả `welcome` kèm `uid`; tên bạn bay lên màn chiếu. **Đó là điểm danh.**

## 2. Đọc code: một gói tin đi đâu

| Bước | File, hàm | Nhìn dòng |
|---|---|---|
| Client mở connection tới server | `client/index.html` → `connect()` | `new WebSocket(…)`, `ws.send(text)` |
| Chữ được đóng thành khung nhị phân | `src/Frame.java` → `encodeText()` / `decode()` | `getBytes(UTF_8)`, byte đầu `0x81`, độ dài, mask |
| Đi qua network, server nhận byte | `src/Net.java` → `listen()` / `serve()` | `accept()`, `in.read(buf)`, một luồng một kết nối |
| Server decode và xử lý | `src/Net.java` → vòng `Frame.decode` → `handler.onMessage` | `GameServer.onMessage(client, text)` |

Bạn không sửa Net/Frame. Đọc một lần để biết dưới `onMessage` có gì.

## 3. Workshop 0.1 — viết server của nhóm

**Chạy:** nhấp đúp **`run.bat`** (Mac/Linux: `./run.sh`). Dòng cuối phải là
`[net] dang nghe / listening on ws://localhost:9000`. Để cửa sổ đó mở. Tắt: `Ctrl-C`.

**Client: ô Máy chủ gõ `localhost:9000` → Connect**, gửi gói `call` → chưa có gì trả về: boilerplate chưa
trả lời. Việc của bạn nằm trong `src/GameServer.java`, hàm `handleCall`:

| TODO | Làm gì | Gói tin |
|---|---|---|
| 1 | đọc `name`, `group`; cấp uid bằng `lastUid.incrementAndGet()`; `online.put(client, uid)`; trả lời **riêng** người gọi rồi `broadcastOnline()` | `{"cmd":"welcome","uid":1,"name":"…","group":"…"}` |
| 2 | thiếu `name` (null hoặc rỗng) → gửi `MISSING_NAME` và `return`, không cấp uid | `{"cmd":"error","text":"…"}` |

Cho sẵn: map `online` (kết nối ↔ uid), `lastUid`, `sendAll`, `broadcastOnline` (số online cho tất cả),
`onClose` (thoát thì xoá khỏi map), `handleBroadcast` (server trả lời từng người), và nhánh
`error` cho lệnh lạ trong `onMessage`.

Vòng lặp làm việc: **sửa → Ctrl-C → run.bat → F5 client → Gửi.** Code chỉ đổi khi biên dịch lại.

Thử với 2–3 tab client: mỗi tab một uid, đóng một tab thì số online của các tab kia giảm.

## 4. Kiểm tra

| | Windows | Mac / Linux |
|---|---|---|
| test tự động (không cần server) | **`test.bat`** | **`./test.sh`** |
| chấm bài (server đang chạy) | mở `/checker` trên ws-2026-server.zingplay.dev, tên, `localhost:9000`, **Kiểm tra** | |

Test 1 = TODO 1, test 2 = TODO 2, test 3–4 = phần cho sẵn. Chấm: 3 mức (welcome · error · online),
qua mức nào tên lên màn chiếu.

## 5. Nhóm được chọn: push git, code của nhóm chạy trên ws-2026-server.zingplay.dev

Khi server của nhóm chạy và client nối vào `localhost:9000` nhận `welcome`, client tự báo về ws-2026-server.zingplay.dev:
nhóm bạn chuyển **🟢** trong cột **"Điểm danh"** trên màn chiếu. Bấm **Random** trên `/admin` →
wheel quay → nhóm được chọn thấy **pháo hoa** trên client. Nhóm đó:

```
git init
git add .
git commit -m "server nhom X"
git remote add origin <URL repo cua nhom>
git push -u origin main
```

Gửi URL repo cho người điều khiển `/admin`. Code được build và chạy code đó ngay trên ws-2026-server.zingplay.dev, tại địa chỉ
`ws-2026-server.zingplay.dev/g/<tên nhóm>`. Cả lớp gõ địa chỉ đó vào ô Máy chủ, Gửi `call`, cùng xem số online
tăng trên server của nhóm. Nhóm lên trình bày 3 phút: `handleCall` của nhóm làm gì.

## Có gì trong thư mục này

```
run.bat / run.sh          biên dịch + chạy server (cổng 9000)
test.bat / test.sh        chạy Tests.java
client/index.html         client kiểu Postman — mở bằng trình duyệt, gõ JSON, đọc log
src/GameServer.java       SERVER CỦA NHÓM — hai TODO trong handleCall
src/Net.java              WebSocket: accept, bắt tay, đọc byte (cho sẵn)
src/Frame.java            byte ↔ tin nhắn (cho sẵn)
lib/gamelogic.jar         thư viện game-logic (dùng ở Workshop 1)
tests/src/Tests.java      test Java thuần
Dockerfile, .gitignore    để deploy server của nhóm ở nơi khác (tuỳ chọn)
tools/Run.java            bộ khởi động đa nền tảng: javac / Gradle / Maven (tuỳ chọn)
build.gradle.kts          mở bằng IntelliJ hoặc chạy bằng Gradle (tuỳ chọn)
```

Tuỳ chọn Gradle/IntelliJ: `java tools/Run.java gradle run` (tự tải Gradle nếu máy chưa có),
`java tools/Run.java gradle tests`.

## Hay gặp

| Triệu chứng | Nguyên nhân |
|---|---|
| *Cannot connect* / *Failed* | server chưa chạy (cửa sổ run.bat đã tắt?), hoặc ô Máy chủ không phải `localhost:9000`. Sửa rồi bấm **Connect** lại |
| Gửi `call` mà log không có gì trả về | boilerplate chưa trả lời — làm TODO 1 |
| sửa code mà không thấy đổi | chưa Ctrl-C và chạy lại `run.bat` |
| `Address already in use` | server cũ còn chạy — tắt cửa sổ cũ, hoặc `run.bat 9001` |
| tên có dấu làm hỏng JSON | bọc mọi chuỗi bằng `Net.esc(...)` |
| thiếu tên mà vẫn nhận welcome | TODO 2: kiểm tra `name` trước khi cấp uid |
