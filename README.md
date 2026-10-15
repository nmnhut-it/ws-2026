# Workshop 2 — Many players at once

Two halves. **First the player's data**: what the server knows about you, written to a database and
read back at every login. **Then the race**: the first group to send `{"cmd":"claim"}` wins the prize.
That code is given to you and it is wrong — when several groups claim at the same moment, the server
tells every one of them that it won.

| TODO | What you do | File |
|---|---|---|
| **1** | save and read the player profile through `Db` | `Players.java` |
| **2** | lifetime wins: `wins()` reads them, `addWin()` adds one | `Players.java` |
| **3** | use them: the `login` carries the count, a win is written down | `GameServer.java` |
| **4** | one queue, one worker: the race lives on a single thread | `Race.java` |

Do them in that order. TODO 1-3 need nothing but a login, so you can test them alone at your desk;
TODO 4 is the one the whole room will try to break.

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

## 2. TODO 1, 2 and 3 — the player data

| TODO | Where | What to do |
|---|---|---|
| 1 | `Players` | `visit(name, group)`: read the old profile; nothing yet means visits = 1, otherwise add 1, and write it back with `db.set`. `load(name)`: read with `db.get`, return `null` when nothing is stored |
| 2 | `Players` | `wins(group)`: read `WINS_PREFIX + group` with `db.get`, nothing saved means **0**. `addWin(group)`: read the old count, add one, write it back, return the new count |
| 3 | `GameServer` | in `handleLogin`, one line: `out.addProperty("wins", lifetime(s));`. In `saveWin(group)`, one line: `players.addWin(group)` inside a try/catch |

`Db` (given) speaks the Redis protocol in plain Java, with two commands only: `set` and `get`.
`handleLogin()` already calls `players.visit(...)` and puts the count in the `login` packet. Come
back with the same name and the client shows the count going up — restart the server and it is still
right, because it lives in the database and not in RAM.

`Players.slogan(name)` is **given**, and it is the same idea in two lines: the first time a name
checks in the server picks a title at random and writes it under `slogan:<name>`; every later
check-in reads that key back. Stop the server, start it again, check in with the same name — same
title, because it was never in RAM. A different name gets its own.

**Read at login (TODO 3).** A login is a database read. `handleLogin()` already looks up the visit
count and the title; you add the third one, how many races your **group** has ever won:

```java
out.addProperty("wins", lifetime(s));
```

`lifetime(s)` is written for you (it calls your `players.wins(...)` and swallows the IOException).

**Write when you win (TODO 3 again).** `saveWin(group)` is empty. The given `handleClaim()` calls it
the moment your group wins a race. One line inside a try/catch:

```java
players.addWin(group);
```

The winner of the race that is running right now lives in RAM and disappears on restart; the count of
races your group has ever won does not. That difference is the whole lesson: decide what has to
outlive the process, and write only that.

Tests 1, 2 and 3 green means this half is done. You can do all of it alone at your desk.

## 3. Look at the bug first

Run `test.bat`. Section 5 lets a dozen groups claim the prize at the same instant and prints how many
of them were told they won. It is 12 of 12. That is a real bug in the given code, not a broken machine.

Read `Race.take()`:

```java
String seen = winner;   // read:  has anybody won yet?
slow();                 // the server is busy right here
if (seen != null) return false;
winner = group;         // write: the winner is this group
```

And read `Race.submit()` — it calls the decision straight away, on whatever thread happened to deliver
the packet. So a dozen connection threads walk into `take()` together, all read the same empty
`winner`, and every one of them writes its own name in.

`slow()` is a sleep, and it is there on purpose. A real server is slow in exactly that spot: it reads
a database, calls another service, or the machine is loaded. The sleep does not create the bug, it
only makes it big enough to see. **Deleting it is not the fix** — it just hides the bug until the day
a real server is busy.

## 4. TODO 4 — give the race one owner

The rule: **data that is shared should be handled by one thread.** Instead of guarding the dangerous
lines, stop having many threads there at all — hand the data to one thread and let everybody else send
it work. (A lock is the other answer; it is further down, with what it costs you.)

```
connection threads          one queue            one worker
   claim  ───────────▶  [ ][ ][ ][ ]  ───────────▶  take() → answer
   claim  ───────────▶
   claim  ───────────▶
```

In `Race.java`: add a `BlockingQueue<Registry.Player>`. `submit()` puts the player in it and returns
immediately — the connection thread is free again. `run()` is the loop of **one** worker: `take()` a
player off the queue, then `decider.decide(...)`. `start()` is already written for you and runs `run()`
on a daemon thread.

`BlockingQueue` does the waiting for you: `take()` sleeps until something arrives instead of spinning
and burning CPU.

Now nobody races anybody. The worker is the only thread that ever reads or writes `winner`, so there
is no moment where two threads can both see it empty — with no lock anywhere.

This is how game servers are built: one thread owns a room, a match or a world, and everything else
talks to it by sending messages.

### The other answer: a lock

A lock is not wrong, and you will meet it more often in real code. It keeps many threads and guards the
dangerous lines instead:

```java
public void submit(Registry.Player who) {
    slow();                      // 1. the slow part stays OUTSIDE the lock
    decider.decide(who);
}

public boolean take(String group) {
    synchronized (this) {        // 2. one thread at a time in here
        if (winner != null) return false;
        winner = group;          //    the check and the write are ONE step
        told.add(group);
        return true;
    }
}
```

It works, but you have to hold three things yourself:

1. **The slow part must stay outside the lock.** Put `slow()` inside and a dozen groups queue up behind
   each other, 300 ms each. Correct but slow is only half the job.
2. **The check and the write must be in the same block.** Split them and the bug is back.
3. **Every other place that touches `winner` must take the same lock** — `open()`, `winner()`,
   `toldCount()`, `everyoneTold()`. Forget one and nothing warns you; the code runs fine until the day
   the room is full.

The queue asks nothing of you except that no one else touches the data. The lock is shorter when only
one or two places touch it, but it runs on your discipline. Either answer is fine as long as you can
say who is allowed to touch this data.

Two things worth arguing about after it goes green:

- One worker handles claims one at a time, and `slow()` is still inside `take()`, so a dozen claims take
  a dozen times 300 ms. Where would you move the slow part so the worker never waits?
- `reset` still calls `race.open()` straight from the connection thread. Does that need to go through
  the queue too?

## 5. Grading and deploy

`test.bat` (Mac/Linux `./test.sh`) runs every test locally, no network needed; with a network it
also reports your group's level to ws-2026-server.zingplay.dev. `deploy.bat` (`./deploy.sh`) sends
`src/*.java` to the main server: it compiles, runs, grades the 3 levels and prints the result right
away, then opens your group's server at `ws-2026-server.zingplay.dev/g/<group name>` for the whole
class.

## 6. The client — nothing to change

The client is the same one as Workshop 1. `{"cmd":"reset"}` opens a new race on your server,
`{"cmd":"claim"}` tries to win it. Both are in the SAMPLES row; they only fill the box, you press
Send.

## 7. Homework — your own arena client

After the workshop, write the arena client yourself: `homework/client.html` is the page with four
TODOs, and `homework/README.md` explains the packets and how it is checked (connect to the main server
with your client and move around; the wall marks you done).

## What is in this folder

```
run.bat / run.sh          compile + run
stop.bat / stop.sh        stop that server (port 9000, or pass another one)
test.bat / test.sh        Tests.java — 6 sections, then report your group's level to the main server
deploy.bat / deploy.sh    send src/*.java to the main server, grade it, run it at /g/<group name>
group.txt                 GROUP NAME — test and deploy read this file
client/index.html         the Postman-style client — same as Workshop 1
homework/                 homework: write your own arena client (client.html + README.md)
src/GameServer.java       TODO 3 — read at login, write on a win
src/Race.java             TODO 4 — the queue and the worker; the race data is GIVEN
src/Players.java          TODO 1 and 2 — the player profile and the lifetime wins
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
| every group is told it won | TODO 4 is not done: `submit()` still decides on the calling thread |
| the race takes a while with many groups | one worker handles them one at a time, and `slow()` is inside `take()` — that is the trade-off to discuss |
| `claim` answers nothing | you are not logged in on that connection — send `login` first |
| the second claim also wins | `reset` was sent in between, which opens a new race on purpose |
| `login` has no `visits` | TODO 1 is not done: `Players.visit()` still returns `null` |
| `login` has no `wins` | TODO 3 is not done: the line is missing in `handleLogin()` |
| `[db] no Redis` | normal: it uses the file `players.txt`; for Redis run `redis.bat` from demo-scripts |
| *NO JDK* when running `run.bat` | no `javac` on this machine — install JDK 17+ from <https://adoptium.net>, **reopen** the window, run again |
| *No group yet* | open `group.txt`, type your group name (for example `nhom-3`) on one line, save |
