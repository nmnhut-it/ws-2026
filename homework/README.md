# Homework · one first winner, without a lock and without a queue

At the start of the class the red bar told several of you that you were first. The server read
"has anybody been first yet?", waited, and then wrote the winner down -- and everybody who read inside
that wait saw "nobody yet". In class you saw two ways to make that right: a lock (`synchronized`) and
one thread that owns the data (a queue). This homework asks for a third.

**Find a way to have exactly one winner without `synchronized`, without a `Lock`, without a
`Semaphore` and without a queue.**

## The file

`FirstWinner.java` in this folder is a race that is wrong on purpose:

```java
boolean claim(String who) {
    String seen = winner;
    pause();                 // the server is busy here
    if (seen != null) return false;
    winner = who;
    return true;
}
```

Change the `Race` class (the part above the line `---- given: the test`). Keep `pause()` -- deleting it
hides the bug, it does not fix it.

## Run it

From the `w2` folder (JDK 17 or newer, nothing else):

```
java homework/FirstWinner.java
```

It lets 12 threads claim at the same moment, 20 times, and counts how many were told they won. It
also refuses your file if the `Race` part uses one of the forbidden tools. You are done when it prints

```
  PASS  exactly one winner in all 20 rounds
```

## What to hand in

The file that passes, and three or four sentences in your own words: why is your version correct when
the original was not?
