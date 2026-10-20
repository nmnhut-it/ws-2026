# Workshop 2 — Remember the players

The server forgets everything when it stops. This workshop writes what has to outlive the process to a
database and reads it back at every login: who you are, how many times you came, and how many times
your whole group came.

| TODO | What you do | File |
|---|---|---|
| **1** | save and read the player profile through `Db` | `Players.java` |
| **2** | the group's count: `groupVisits()` reads it, `addGroupVisit()` adds one | `Players.java` |
| **3** | use it: every login adds one for the group, and the `login` carries the count | `GameServer.java` |

Do them in that order. All three need nothing but a login, so you can test them alone at your desk.

## 0. Getting the code

```
git clone -b nhom-3-w2 https://github.com/nmnhut-it/ws-2026.git
```

`ws-2-answer` is the solution branch, opened after the session. No git? Download `w2.zip` from
ws-2026-server.zingplay.dev.

The session 2 group branches are `nhom-1-w2` … `nhom-8-w2`; the plain branch with no group is
`ws-2-exercise`. Open **`group.txt`** and check the group name (group branches already have it).
`test.bat` reads that file to report your group's level to the wall, `deploy.bat` reads it to know
where to put the code.

## 1. Run it

Same as Workshop 1: **`run.bat`** (Mac/Linux `./run.sh`), client Server box `localhost:9000` →
**Connect**.

Redis: run the demo project's `RedisSimulator` (`demo-scripts.zip` → `redis.bat`) or a real Redis on
`localhost:6379`. With no Redis the server falls back to the file `players.txt` and still works.

## 2. The three TODOs

| TODO | Where | What to do |
|---|---|---|
| 1 | `Players` | `visit(name, group)`: read the old profile; nothing yet means visits = 1, otherwise add 1, and write it back with `db.set`. `load(name)`: read with `db.get`, return `null` when nothing is stored |
| 2 | `Players` | `groupVisits(group)`: read `GROUP_PREFIX + group` with `db.get`, nothing saved means **0**. `addGroupVisit(group)`: read the old count, add one, write it back, return the new count |
| 3 | `GameServer` | write `groupVisit(s)`: `return players.addGroupVisit(s.group);` inside a try/catch, the way `visits()` is written. In `handleLogin`, one line: `out.addProperty("groupVisits", groupVisit(s));` |

`Db` (given) speaks the Redis protocol in plain Java, with two commands only: `set` and `get`.
`handleLogin()` already calls `players.visit(...)` and puts the count in the `login` packet. Come
back with the same name and the client shows the count going up — restart the server and it is still
right, because it lives in the database and not in RAM.

`Players.slogan(name)` is **given**, and it is the same idea in two lines: the first time a name
checks in the server picks a title at random and writes it under `slogan:<name>`; every later
check-in reads that key back. Stop the server, start it again, check in with the same name — same
title, because it was never in RAM. A different name gets its own.

**The group's count (TODO 2 and 3).** Every login of anybody in your group adds one to
`group:<group name>`, and the `login` answer carries the new total as `groupVisits`. Log in twice
from two tabs with two names in the same group: the second one says 2. Restart the server and log in
again: it says 3, not 1. Who is online right now lives in RAM and is gone after a restart; the count
does not. That difference is the whole lesson: decide what has to outlive the process, and write only
that.

Read then write is two steps. Ask yourself what happens when two people of the same group log in at
the very same moment -- the red bar at the start of the class showed you the same shape.

## 3. Grading and deploy

`test.bat` (Mac/Linux `./test.sh`) runs every test locally, no network needed; with a network it
also reports your group's level to ws-2026-server.zingplay.dev. Section 3 also restarts the server
on the same database file and checks that the count goes on. `deploy.bat` (`./deploy.sh`) sends
`src/*.java` to the main server: it compiles, runs, grades the 3 levels and prints the result right
away, then opens your group's server at `ws-2026-server.zingplay.dev/g/<group name>` for the whole
class.

## 4. The client — nothing to change

The client is the same one as Workshop 1. From Workshop 2 on, the *You* card shows your `visits` and
your group's `group logins` from the `login` answer.

## 5. Homework — one first winner, without a lock and without a queue

`homework/FirstWinner.java` is the red bar's race, wrong on purpose. Make it give exactly one winner
without `synchronized`, a `Lock`, a `Semaphore` or a queue, and run it with
`java homework/FirstWinner.java` until it prints PASS. `homework/README.md` says what to hand in.

## What is in this folder

```
run.bat / run.sh          compile + run
stop.bat / stop.sh        stop that server (port 9000, or pass another one)
test.bat / test.sh        Tests.java — 3 sections, then report your group's level to the main server
deploy.bat / deploy.sh    send src/*.java to the main server, grade it, run it at /g/<group name>
group.txt                 GROUP NAME — test and deploy read this file
client/index.html         the Postman-style client — same as Workshop 1
homework/                 homework: FirstWinner.java (one winner, no lock, no queue) + README.md
src/GameServer.java       TODO 3 — groupVisit(), and the login carries the count
src/Players.java          TODO 1 and 2 — the player profile and the group's count
src/Db.java               Redis (RESP) or a file, set/get only — given
                          Players.slogan() is given too: the title kept per name
src/Registry.java, Net.java, Connection.java, Handshake.java, Frame.java   given
Dockerfile, .gitignore    deploy your group's server yourself (optional)
tools/Run.java, build.gradle.kts   Gradle / IntelliJ (optional)
tools/Report.java, Deploy.java     send results / send code to the main server (given)
```

## Common problems

| Symptom | Cause |
|---|---|
| `login` has no `visits` | TODO 1 is not done: `Players.visit()` still returns `null` |
| `login` has no `groupVisits` | TODO 3 is not done: the line is missing in `handleLogin()` |
| `groupVisits` is always 0 | `groupVisit()` or `addGroupVisit()` still returns 0 |
| `groupVisits` goes back to 1 after a restart | the count is kept in a variable, not read from `db` |
| two groups share one count | the key must be `GROUP_PREFIX + group`, one per group |
| `[db] no Redis` | normal: it uses the file `players.txt`; for Redis run `redis.bat` from demo-scripts |
| *NO JDK* when running `run.bat` | no `javac` on this machine — install JDK 17+ from <https://adoptium.net>, **reopen** the window, run again |
| *No group yet* | open `group.txt`, type your group name (for example `nhom-3`) on one line, save |