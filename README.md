# Workshop 2 — Many players

Two halves. **First the player's data**: what the server knows about you, written to a database and
read back at every login. **Then the fight**: the boss code is given to you and it is wrong — when many
players attack at once, strikes go missing and two players are both announced as the killer.

| TODO | What you do | File |
|---|---|---|
| **1** | save and read the player profile through `Db` | `Players.java` |
| **2** | lifetime damage: `damage()` reads it, `addDamage()` adds to it | `Players.java` |
| **3** | use them: load the total into the `login`, save the board when a boss dies | `GameServer.java` |
| **4** | one queue, **one** worker handling strikes in order | `FightQueue.java` |
| **5** | `handleAttack` puts the strike in the queue instead of striking directly | `GameServer.java` |

Do them in that order. TODO 1-3 need nothing but a login, so you can test them alone at your desk;
TODO 4-5 need a crowd hitting one boss.

The fight panel in the client is opened for the whole room from the wall when the time comes; there
is nothing for you to type.

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
**Connect**. The summon threshold is **40** (grading opens 45 connections). Testing alone:
temporarily change `-Dbeast.threshold=40` to `3` in `run.bat`, and change it back before grading.

Redis: run the demo project's `RedisSimulator` (`demo-scripts.zip` → `redis.bat`) or a real Redis on
`localhost:6379`. With no Redis the server falls back to the file `players.txt` and still works.

## 2. TODO 1, 2 and 3 — the player data

| TODO | Where | What to do |
|---|---|---|
| 1 | `Players` | `visit(name, group)`: read the old profile; nothing yet means visits = 1, otherwise add 1, and write it back with `db.set`. `load(name)`: read with `db.get`, return `null` when nothing is stored |
| 2 | `Players` | `damage(name)`: read `DAMAGE_PREFIX + name` with `db.get`, nothing saved means **0**. `addDamage(name, amount)`: read the old total, add, write it back, return the new total |
| 3 | `GameServer` | in `handleLogin`, one line: `out.addProperty("damage", lifetime(s));`. In `saveDamage()`, walk `fight.board(BOARD_ALL)` and `bank(e.name, e.total)` for each row |

`Db` (given) speaks the Redis protocol in plain Java, with two commands only: `set` and `get`.
`handleLogin()` already calls `players.visit(...)` and puts the count in the `login` packet. Come
back with the same name and the client shows the count going up — restart the server and it is still
right, because it lives in the database and not in RAM.

`Players.slogan(name)` is **given**, and it is the same idea in two lines: the first time a name
checks in the server picks a title at random and writes it under `slogan:<name>`; every later
check-in reads that key back. Stop the server, start it again, check in with the same name — same
title, because it was never in RAM. A different name gets its own.

**Load at login (TODO 3).** A login is a database read. `handleLogin()` already looks up the visit
count and the title; you add the third one, your **lifetime damage** — every point you have ever
dealt to a boss, in any fight, before any restart. One line, right where the other two are:

```java
out.addProperty("damage", lifetime(s));
```

`lifetime(s)` is written for you (it calls your `players.damage(...)` and swallows the IOException).
The `login` carries the number, the client prints it, and nothing is remembered in the browser: the
server looks it up every single login.

**Save when the fight ends (TODO 3 again).** `saveDamage()` is empty. The given kill path calls it
once, the moment a boss dies. Walk the damage board and hand each row to `bank(name, amount)`:

```java
for (BeastState.Entry e : fight.board(BOARD_ALL)) bank(e.name, e.total);
```

One write per fighter per fight — **not** one per strike. `Db.FileDb.set` rewrites the whole file, and
a fight is hundreds of strikes; writing on every hit would crawl. Ask yourself where else that choice
shows up in real servers.

So the per-fight board still lives in RAM and disappears on restart, while the lifetime total does
not. That difference is the whole lesson: decide what has to outlive the process, and write only that.

Tests 1, 2 and 3 green means this half is done. You can do all of it alone at your desk — no crowd,
no boss needed.

## 3. Look at the bug first

Run `test.bat`. The two given-code tests are green: the boss code strikes correctly on a single
thread. The next one lets 20 threads call `strike()` at once and prints a `LOST n damage` line, or a
`ConcurrentModificationException`. That is a real bug in the given code, not a broken machine.

Read `BeastState.strike()`: `hp -= dealt` is three steps — read the hp, subtract, write it back. Two
threads slipping between those steps lose a strike. Connect the whole class to one group's server and
attack: sometimes **two players are both announced as the killer**.

## 4. TODO 4 and 5 — the queue

| TODO | Where | What to do |
|---|---|---|
| 4 | `FightQueue` | add a `BlockingQueue<Registry.Player>`; `submit()` puts it in the queue and returns; `run()` is the loop of **one** worker: `take()` → `handler.handle()`; `pending()` = size |
| 5 | `GameServer.handleAttack()` | change `doStrike(s)` to `queue.submit(s)` — connection threads never touch `BeastState` again |

Do not add `synchronized` anywhere. With one thread touching the fight there is nobody left to race
against. The last test (16 threads submitting, exactly one killer) turning green means you are done.

## 5. Grading and deploy

`test.bat` (Mac/Linux `./test.sh`) runs every test locally, no network needed; with a network it
also reports your group's level to ws-2026-server.zingplay.dev. `deploy.bat` (`./deploy.sh`) sends
`src/*.java` to the main server: it compiles, runs with threshold 40, grades the 3 levels and prints
the result right away, then opens your group's server at
`ws-2026-server.zingplay.dev/g/<group name>` for the whole class.

## 6. The client — nothing to change

The client is the same one as Workshop 1. Attacking is `{"cmd":"attack"}` then Enter; **hold Enter**
to attack repeatedly. The panel the instructor opens only draws the hp bar and the damage board out of
the `beast` / `slain` packets you already see in the log.

## What is in this folder

```
run.bat / run.sh          compile + run (threshold 40)
stop.bat / stop.sh        stop that server (port 9000, or pass another one)
test.bat / test.sh        Tests.java — 5 tests, then report your group's level to the main server
deploy.bat / deploy.sh    send src/*.java to the main server, grade it, run it at /g/<group name>
group.txt                 GROUP NAME — test and deploy read this file
client/index.html         the Postman-style client — same as Workshop 1
src/GameServer.java       TODO 3 — load at login, save on kill; TODO 5 — move handleAttack onto the queue
src/FightQueue.java       TODO 4 — the queue + worker
src/Players.java          TODO 1 and 2 — the player profile and the lifetime damage
src/BeastState.java       the boss fight — GIVEN (and deliberately not thread safe)
src/Db.java               Redis (RESP) or a file, set/get only — given
                          Players.slogan() is given too: the title kept per name
src/Registry.java, Net.java, Frame.java   given
lib/gamelogic.jar         conjure() + rollDamage(uid, strikeNo) — 10..60, pure
Dockerfile, .gitignore    deploy your group's server yourself (optional)
tools/Run.java, build.gradle.kts   Gradle / IntelliJ (optional)
tools/Report.java, Deploy.java     send results / send code to the main server (given)
```

## Common problems

| Symptom | Cause |
|---|---|
| grading says *no boss appeared* | start with `run.bat` (threshold 40); summoning is given code, do not change it |
| `ConcurrentModificationException` in the console | many threads writing one `HashMap` — TODO 4 and 5 fix it with the queue |
| two `slain` messages | TODO 5 is not done: strikes still go straight to `doStrike` instead of `FightQueue` |
| `login` has no `visits` | TODO 1 is not done: `Players.visit()` still returns `null` |
| `login` has no `damage` | TODO 3 is not done: the line is missing in `handleLogin()` |
| `[db] no Redis` | normal: it uses the file `players.txt`; for Redis run `redis.bat` from demo-scripts |
| *NO JDK* when running `run.bat` | no `javac` on this machine — install JDK 17+ from <https://adoptium.net>, **reopen** the window, run again |
| *No group yet* | open `group.txt`, type your group name (for example `nhom-3`) on one line, save |
