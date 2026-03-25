# Basic Bot

A small Matrix bot example for `trixnity-clj`.

This is a good first example for learning missionary and trixnity-clj. It
shows the basic mental model: run one-shot client operations with `m/?`, then
consume ongoing Matrix updates as long-lived flows once sync is ready.

It demonstrates:

- open a client with the built-in sqlite4clj repository
- start sync and wait for `RUNNING`
- auto-join invited rooms
- reply to a few simple text commands

## Requirements

Set these environment variables:

- `MATRIX_HOMESERVER_URL`
- `MATRIX_BOT_USER_ID`
- `MATRIX_BOT_PASSWORD`

## Run

From this directory:

```bash
clojure -X:deps prep
clojure -M:run
```

The bot stores local state under `./dev-data/basic-bot/`.
