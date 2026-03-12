# trixnity-clj

> A Clojure adapter for [Trixnity][trixnity], a Matrix SDK. Write matrix apps/bots in Clojure.

[Trixnity][trixnity] is a Kotlin Multiplatform library for building Matrix
applications. It covers the full matrix client-server API surface, including
room management, sync, and end-to-end encryption. Despite flying under the
radar, Trixnity already powers a large chunk of Germany's TI-Messenger
healthcare infrastructure, reaching tens of millions of potential users.

Trixnity targets the JVM, which means Clojure can call into it directly, but the
interop is still rough. Nearly every interesting API is either a Kotlin suspend
function or returns a `Flow`/`StateFlow`, so even basic client work quickly
turns into coroutine plumbing: scopes, dispatchers, cancellation, and stream
observation.

The flow-heavy parts are especially awkward because they are long-lived Kotlin
stream types, not something Clojure can consume idiomatically.
So things like sync state, timeline updates, and other live observations need
extra translation. Kotlin's compiler also mangles names in the compiled
bytecode, which makes direct interop harder when reading the upstream docs. 


`trixnity-clj` aims to wrap Trixnity with a small Kotlin bridge and expose the concurrency using [Missionary][missionary]. It maps Kotlin coroutines into [Missionary tasks][miss-tasks] and Kotlin Flows to [Missionary flows][miss-flow].

(Also i wanted an excuse to use Missionary in a real application)

The public clojure API attempts to mirror the Trixnity service layout, so when reading Trixnity code/docs it should be more or less obvious where the corresponding clojure functions are.

Kotlin suspend functions always return Missionary tasks.
Kotlin `Flow` and `StateFlow` surfaces always return Missionary flows.
`StateFlow` properties are exposed as a synchronous `current-*` getter plus a relieved Missionary flow.

Project Status: [Experimental](https://docs.outskirtslabs.com/open-source-vital-signs#experimental)

## Repository Backend

In Trixnity, a repository module is the persistence backend for all the client
state it needs to keep around: sync tokens, rooms, timelines, outbox entries,
encryption keys, notifications, and the rest of the Matrix baggage.

Upstream ships several of these across platforms. If you want durable storage on
the JVM, the stock option is the [Exposed repository module][trixnity-exposed],
which means JetBrains's ORM Exposed layered on top of JDBC.

Maybe that sparks joy for somebody. It does not spark joy for me.

For the kind of bots and small clients I am building, running SQLite through
JDBC makes write serialization harder than it should be, which means more
busy_timeout pain. 

And god forbid anyone suggest dragging Hikari into that mess.

So this library includes its own repository implementation backed by
[sqlite4clj][sqlite4clj], and that is the default setup.

The storage layer is just plain SQLite, explicit SQL, and a lot less nonsense.

Shout out to Anders Murphy for building [sqlite4clj][sqlite4clj], and making the SQLite story in Clojure 100% less deranged.

## Example Usage

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


## License: Apache License 2.0

Copyright © 2026 Casey Link

Distributed under the [Apache-2.0](https://spdx.org/licenses/Apache-2.0.html) just like Trixnity itself.

[trixnity]: https://trixnity.connect2x.de/
[trixnity-exposed]: https://gitlab.com/trixnity/trixnity/-/tree/main/trixnity-client/trixnity-client-repository-exposed
[missionary]: https://github.com/leonoel/missionary/
[miss-task]: https://github.com/leonoel/missionary/blob/master/doc/tutorials/hello_task.md
[miss-flow]: https://github.com/leonoel/missionary/blob/master/doc/tutorials/hello_flow.md
[sqlite4clj]: https://github.com/andersmurphy/sqlite4clj
