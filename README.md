# trixnity-clj

> A Missionary-first Clojure adapter for [Trixnity][trixnity], a Matrix SDK.

`trixnity-clj` wraps the JVM flavor of Trixnity with a small Kotlin bridge and a
public Clojure API that mirrors Trixnity's service layout:

- `ol.trixnity.client`
- `ol.trixnity.room`
- `ol.trixnity.user`
- `ol.trixnity.notification`
- `ol.trixnity.verification`
- `ol.trixnity.key`

Suspend functions return Missionary tasks. Kotlin `Flow` and `StateFlow`
surfaces return Missionary flows. `StateFlow` properties are exposed as a
synchronous `current-*` getter plus a relieved Missionary flow.

Project Status: [Experimental](https://docs.outskirtslabs.com/open-source-vital-signs#experimental)

## Happy Path

Use [`ol.trixnity.repo`](/home/ramblurr/src/github.com/outskirtslabs/trixnity-clj/src/clj/ol/trixnity/repo.clj)
to configure the built-in sqlite4clj repository, then open a client with
[`ol.trixnity.client/open`](/home/ramblurr/src/github.com/outskirtslabs/trixnity-clj/src/clj/ol/trixnity/client.clj):

```clojure
(ns my.bot
  (:require
   [missionary.core :as m]
   [ol.trixnity.client :as client]
   [ol.trixnity.event :as event]
   [ol.trixnity.repo :as repo]
   [ol.trixnity.room :as room]
   [ol.trixnity.room.message :as msg]
   [ol.trixnity.schemas :as mx])
  (:import
   [java.time Duration]))

(defn run-bot []
  (let [client (m/? (client/open
                     (merge
                      {::mx/homeserver-url "https://matrix.example.org"
                       ::mx/username       "bot"
                       ::mx/password       "secret"}
                      (repo/sqlite4clj-config
                       {:database-path "./var/trixnity.sqlite"
                        :media-path    "./var/media"}))))]
    (m/? (client/start-sync client))
    (m/? (client/await-running client
                               {::mx/timeout (Duration/ofSeconds 30)}))
    (future
      (m/? (m/reduce
            (fn [_ ev]
              (when (event/text? ev)
                (m/? (room/send-message
                      client
                      (event/room-id ev)
                      (-> (msg/text "pong")
                          (msg/reply-to ev))
                      {::mx/timeout (Duration/ofSeconds 5)})))
              nil)
            nil
            (room/get-timeline-events-from-now-on
             client
             {::mx/decryption-timeout (Duration/ofSeconds 8)}))))
    {:client client}))
```

If you want a different repository implementation, construct a `MatrixClient`
yourself and pass it to `client/open` as `::mx/client`.

## API Notes

- `client/open`, `client/start-sync`, `client/await-running`, `client/stop-sync`, and `client/close` return Missionary tasks.
- `client/profile`, `client/server-data`, `client/sync-state`, `client/initial-sync-done`, `client/login-state`, and `client/started` are Missionary flows.
- `room/get-all` preserves Trixnity's keyed nested room-flow shape.
- `room/get-all-flat`, `room/get-outbox-flat`, `room/get-timeline-events-list`, `room/get-last-timeline-events-list`, and `room/get-timeline-events-around` are additive helpers for snapshot/list-oriented consumers.
- `ol.trixnity.event` remains a small accessor namespace for normalized live event maps.

## License: Apache License 2.0

Copyright © 2026 Casey Link

Distributed under the [Apache-2.0](https://spdx.org/licenses/Apache-2.0.html) just like Trixnity itself.

[trixnity]: https://trixnity.connect2x.de/
