# Homework · write your own arena client

In class you moved a dot around the arena with the client we gave you. Now write that client yourself.
`client.html` in this folder already has the page, the canvas, the draw loop and the keyboard. You fill in
four functions. Open the file in a browser (double-click it); no server or build step is needed.

## What the server speaks

| You send | When |
|---|---|
| `{"cmd":"login","name":"An","group":"nhom-3","client":"my-client"}` | once, after the socket opens |
| `{"cmd":"move","x":1,"y":0}` | every time the keys you hold change; `x` and `y` are each -1, 0 or 1 |

| The server sends | What it means |
|---|---|
| `{"cmd":"login","uid":7,...}` | you are in; `uid` is your dot |
| `{"cmd":"roster","group":"nhom-3","w":960,"h":540,"r":16,"players":[{"uid":7,"name":"An"}]}` | the size of the field, your room and your name; sent once when you join |
| `{"cmd":"world","p":[[7,480,270]]}` | where you are, `[uid, x, y]`, 10 times a second. Only you: the wall is where everybody shows |
| `{"cmd":"error","text":"..."}` | something you sent was wrong |

You never send a position. You send the keys you hold, and the server works out speed, acceleration,
walls and bumps for everybody. That is why nobody can teleport: the server owns the positions.
`{"cmd":"bump","with":"Minh"}` arrives when the server finds you ran into somebody.

## The four TODOs

1. `connect()` -- open the WebSocket and send the login packet.
2. `onPacket(m)` -- keep your uid, the field size, the names and the positions.
3. `sendKeys()` -- turn the held keys into `x` and `y`, and send a `move` only when they change.
4. `drawPlayers(ctx, scale)` -- a circle for every entry in `world` (today that is you), your name above it.

## How it is checked

Connect to `ws-2026-server.zingplay.dev` with your client and move around. Once your client has sent
20 `move` packets, the main server marks you as done (it knows it is your client because the login
carries `"client"`), and the wall shows it. Keep `"client"` set, or you are not counted.

## If you want more (optional)

- The dots jump 10 times a second. Draw them between two `world` packets so they glide.
- Show your own speed, or how many packets a second you receive.
- Flash something when a `bump` arrives.
- Reconnect by itself when the connection drops.
- The questions on the last slide: what happens to old clients when the server changes its packets,
  and what changes when one server is not enough.