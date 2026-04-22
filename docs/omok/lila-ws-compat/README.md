# Omok lila-ws compatibility patch

This folder preserves the lila-ws changes required by the Omok.dev round UI.

The Omok UI sends these round socket messages:

- `place` with `{ pos: "H8" }`
- `omok-swap`
- `omok-candidates`

The lila server on this branch already accepts:

- `r/place <fullId> <pos>` and routes it to `HumanPlace`
- `r/do <fullId> ...` for `omok-swap`
- `r/do <fullId> ...` for `omok-candidates`

Apply `lila-ws-omok-compat.patch` to a lila-ws checkout. It changes:

- `src/main/scala/ipc/ClientOut.scala`
- `src/main/scala/ipc/LilaIn.scala`
- `src/main/scala/actor/RoundClientActor.scala`

Compatibility note:

- The patched lila-ws is compatible with the Omok.dev lila branch.
- Vanilla lila-ws is not enough, because it ignores `place` and does not forward the Taraguchi opening actions.
- The patch was generated against upstream `lichess-org/lila-ws` master at `868d2743d9ef8c3ad21c9a5b51ec88e793606bb2`.

Expected protocol flow:

```text
browser -> lila-ws: place { pos: "H8" }
lila-ws -> lila:    r/place <fullId> H8

browser -> lila-ws: omok-swap
lila-ws -> lila:    r/do <fullId> {"t":"omok-swap",...}

browser -> lila-ws: omok-candidates
lila-ws -> lila:    r/do <fullId> {"t":"omok-candidates",...}
```
