# Workshop 1 — Client calls server

You get **one client** (a single HTML file) and **one server** (plain Java, a boilerplate that
already runs). Today:

| Part | What you do |
|---|---|
| Workshop 0 · check in | send ws-2026-server.zingplay.dev **one JSON packet** with your name and group |
| Workshop 1 · your group's server | `GameServer.java`: answer with `login`, answer with `error` when the packet is wrong |
| Deploy | `deploy.bat` pushes your group's code to ws-2026-server.zingplay.dev, it runs at `/g/<group name>` and the whole class connects |

No Gradle, no Maven, no downloads. A JDK is all you need.

---

## Getting the code

```
git clone -b ws-1-exercise https://github.com/nmnhut-it/ws-2026.git
```

Branches: `ws-1-exercise` (today) · `ws-1-answer` (the solution, opened after the session) ·
`ws-2-exercise` · `ws-2-answer`.
No git? Download `w1.zip` from ws-2026-server.zingplay.dev — same content.

Your group has its own branch `nhom-1-w1` … `nhom-8-w1`:
`git clone -b nhom-3-w1 https://github.com/nmnhut-it/ws-2026.git`.
(Session 2 is a second set: `nhom-3-w2`.)
A group branch is the same exercise, only **`group.txt`** already holds the group name. If you took
`ws-1-exercise` or `w1.zip`, open `group.txt` and type your group name in — grading and deploy both
read that file.

## 0. Check your machine (before class)

Open a **terminal** (Windows: type `cmd` in the search box) and run:

```
javac -version
```

It must print `javac 17` or newer (`javac 21.0.x` is good). If it says *not recognized* /
*command not found*, install a JDK: <https://adoptium.net> → Latest LTS → default options →
**reopen the terminal** and try again.

## 1. Check in — send one packet to ws-2026-server.zingplay.dev

1. Open the client (address on the wall, or double-click **`client/index.html`**). It works like
   Postman: **one JSON box**, a **Send** button and a **log**. Above the box is a row of sample
   packets — pressing one **fills** the box so you can read it, but sending is still your call.
2. **Server** box = the address on the wall → **Connect**.
3. F12 → **Network** → **WS** → the `ws` row → **Messages**. Leave that tab open.
4. Type `{"cmd":"login"}` → Enter → the server answers `error`: no name.
5. Type `{"cmd":"login","name":"your name","group":"your group"}` → Enter.
   The server answers `login` with a `uid`, and your name flies onto the wall. **That is the
   check-in.**

Do not want to type it all out: the **Quick check-in** card above the box takes a Name and a Group
and its **Check in** button builds the packet for you. It only fills the box; you still press **Send**.

## 2. Read the code: where a packet goes

| Step | File, method | Line to look at |
|---|---|---|
| The client opens a connection | `client/index.html` → `connect()` | `new WebSocket(…)`, `ws.send(text)` |
| Text is wrapped into a binary frame | `src/Frame.java` → `encodeText()` / `decode()` | `getBytes(UTF_8)`, first byte `0x81`, length, mask |
| Bytes travel, the server receives them | `src/Net.java` → `listen()` / `serve()` | `accept()`, `in.read(buf)`, one thread per connection |
| The server decodes and handles | `src/Net.java` → the `Frame.decode` loop → `handler.onMessage` | `GameServer.onMessage(client, text)` |

You do not touch Net or Frame. Read them once so you know what sits under `onMessage`.

`onMessage` routes on the `cmd` field with one `if` per command. Two are already wired: `login`
(yours to finish) and `ping`, which answers `pong` and exists so you can see a second handler next
to yours. Anything else gets an `error` back.

## 3. Write your group's server

**Run:** double-click **`run.bat`** (Mac/Linux: `./run.sh`). The last line must be
`[net] listening on ws://localhost:9000`. Leave that window open. Stop it with `Ctrl-C`.

**In the client, set the Server box to `localhost:9000` → Connect**, send a `login` — nothing comes
back: the boilerplate does not answer yet. Your work is four empty methods in `src/GameServer.java`.

### The session

A WebSocket connection stays open. The server can therefore remember something about it and use that
memory on the **next** packet. That memory is the **session**: one `Session` object per connection,
holding the uid, the name, the group and the moment the login happened.

```
sessions :  Net.Client  ->  Session { uid, name, group, since }
```

`login` opens a session. `getUserInfo` proves it exists: the packet is only `{"cmd":"getUserInfo"}` — no name,
no uid — and the server still answers with your name, because it looked the connection up in
`sessions`. `logout` throws the session away while the connection stays open, so `getUserInfo` fails
again. Nothing about you is stored in the client.

| TODO | Method | What to do | Packet you send back |
|---|---|---|---|
| 1 | `handleLogin` | read `name` and `group`; uid = `lastUid.incrementAndGet()`; `sessions.put(client, new Session(uid, name, group))`; answer **only** the caller, then `broadcastOnline()` | `{"cmd":"login","uid":1,"name":"…","group":"…"}` |
| 2 | `handleLogin` | no `name` → send `MISSING_NAME`; no `group` → send `MISSING_GROUP`; both then `return`, no session | `{"cmd":"error","text":"…"}` |
| 3 | `handleGetUserInfo` | `sessions.get(client)`; no session → `error(NOT_LOGGED_IN)`; otherwise answer out of the session, never out of the packet | `{"cmd":"getUserInfo","uid":1,"name":"…","group":"…","seconds":12}` |
| 4 | `handleLogout` | no session → `error(NOT_LOGGED_IN)`; otherwise `sessions.remove(client)`, answer, then `broadcastOnline()` | `{"cmd":"logout","uid":1}` |

`name` and `group` are both required; null and empty string both count as missing. All three error
lines are already written for you in `MISSING_NAME`, `MISSING_GROUP` and `NOT_LOGGED_IN` — you only
send them. `Session.seconds()` gives you the number for TODO 3.

Given to you: `Session.java`, the `sessions` map, `lastUid`, `sendAll`, `broadcastOnline` (the online
count for everyone), `onClose` (closing the tab drops the session too), `handleBroadcast` (the server
answers each client), `handlePing`, and the `error` branch for unknown commands in `onMessage`.

Not sure how to build a packet? Read `handleBroadcast()` right below `handleLogin`: it creates a
`JsonObject`, adds one field at a time with `addProperty`, then calls `client.send(out.toString())`.
A login answer works exactly the same way, only the fields differ.

The work loop: **edit → Ctrl-C → run.bat → F5 the client → Send.** Code only changes after a
recompile.

Try it with 2–3 client tabs: each tab gets its own uid and its own session, and closing one drops
the online count in the others. In one tab send `getUserInfo`, then `logout`, then `getUserInfo` again — the
same connection, two different answers.

## 4. Checking your work

| | Windows | Mac / Linux |
|---|---|---|
| automated tests (no server needed) | **`test.bat`** | **`./test.sh`** |
| push the code and grade it on the main server | **`deploy.bat`** | **`./deploy.sh`** |

Test 1 = TODO 1, test 2 = TODO 2, test 3 = TODO 3 and 4, tests 4–5 cover the given code. Grading
has 3 levels (login · errors · session); every level you pass puts your name on the wall.

`test.bat` runs **without a network**. With a network it also reports your group's level to
ws-2026-server.zingplay.dev (reading the group name from `group.txt`) and your group shows up on the
wall. Without a network it prints one extra "could not report" line — the test result above it is
still valid.

## 5. The chosen group: `deploy.bat` — your code running on the main server

Once your server is running and a client connected to `localhost:9000` gets a `login`, that client
reports back to ws-2026-server.zingplay.dev and your group turns **🟢** in the **Check-in** column on
the wall. The instructor picks one of the green groups. That group double-clicks **`deploy.bat`**
(Mac/Linux `./deploy.sh`).

It sends your `src/*.java` to the main server, which compiles it, runs it, **grades all 3 levels**
and prints the result right in that window. If the compile fails it prints the exact `javac` line.

Then the whole class types `ws-2026-server.zingplay.dev/g/<group name>` in their **Server** box,
sends `login`, and watches the online count climb **on your group's server**. Your group gets 3
minutes to explain what its `handleLogin` does.

Fix the code and run `deploy.bat` again to replace the old build. No git, no GitHub account. (`Dockerfile` is still there if you want
to deploy your server somewhere else.)

## What is in this folder

```
run.bat / run.sh          compile + run the server (port 9000)
stop.bat / stop.sh        stop that server (port 9000, or pass another one)
test.bat / test.sh        run Tests.java, then report your group's level to the main server
deploy.bat / deploy.sh    send src/*.java to the main server, grade it, run it at /g/<group name>
group.txt                 GROUP NAME — test and deploy read this file
client/index.html         the Postman-style client — open it in a browser, type JSON, read the log
src/GameServer.java       YOUR GROUP'S SERVER — four TODOs: login, errors, getUserInfo, logout
src/Session.java          one session per connection — given, read it
src/Net.java              WebSocket: accept, handshake, read bytes (given)
src/Frame.java            bytes <-> messages (given)
lib/gamelogic.jar         the game-logic library (used in Workshop 2)
tests/src/Tests.java      plain Java tests
Dockerfile, .gitignore    to deploy your group's server elsewhere (optional)
tools/Run.java            cross-platform launcher: javac / Gradle / Maven (optional)
tools/Report.java, Deploy.java   send results / send code to the main server (given)
build.gradle.kts          open it in IntelliJ or run it with Gradle (optional)
```

Gradle / IntelliJ option: `java tools/Run.java gradle run` (downloads Gradle if the machine has
none), `java tools/Run.java gradle tests`.

## Common problems

| Symptom | Cause |
|---|---|
| *Cannot connect* / *Failed* | the server is not running (did the run.bat window close?), or the Server box is not `localhost:9000`. Fix it and press **Connect** again |
| you send `login` and the log shows nothing back | the boilerplate does not answer yet — do TODO 1 |
| `getUserInfo` answers `No session on this connection` right after a good login | TODO 1 built the packet but never did `sessions.put(client, …)` |
| you changed the code and nothing changed | you did not Ctrl-C and run `run.bat` again |
| `Address already in use` | an old server is still running — run `stop.bat` (Mac/Linux `./stop.sh`), or use another port with `run.bat 9001` |
| accents in a name break the JSON | build the packet with `JsonObject` instead of gluing strings; Gson handles the quoting |
| a call with no name or no group still gets a login answer | TODO 2: check both fields before taking a uid |
| *NO JDK* when running `run.bat` | no `javac` on this machine — install JDK 17+ from <https://adoptium.net>, **reopen** the window, run again |
| *No group yet* | open `group.txt`, type your group name (for example `nhom-3`) on one line, save |
| `deploy.bat` says *COMPILE FAILED* | `javac` failed on the main server — read the error line it prints, fix it, deploy again |
