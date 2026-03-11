# trixnity-clj

> A Clojure adapter for [Trixnity][trixnity], a Matrix SDK. Write matrix apps/bots in Clojure.

[Trixnity][trixnity] is a Kotlin Multiplatform library for building Matrix applications.
It covers the full matrix client-server API surface, including room management, sync, and end-to-end encryption.
Despite flying under the radar, Trixnity already powers a large chunk of Germany's TI-Messenger healthcare infrastructure, reaching tens of millions of potential users.
Trixnity targets the JVM, which means Clojure can call into it. The interop is rough, though.

Trixnity targets the JVM (among other platforms, but I'm interestested in the JVM), which means Clojure can call into it. But the interop is rough.
Nearly every interesting method is a Kotlin suspend function, which requires wiring up coroutine scopes, dispatchers etc.
Kotlin's compiler mangles names in the compiled bytecode, making it difficult to call things directly from Clojure when reading the docs.
I also wanted to avoid reflection.

So trixnity-clj is a small Kotlin<->Clojure bridge layer that uses `@JvmStatic` annotations to expose stable entry points on the JVM that Clojure can interop with directly.
It wraps Trixnity's coroutine-based APIs and returns `CompletableFuture`s that your Clojure code can `deref` or compose with its own async tooling.
I recommend using [Quiescent][quiescent]'s `q/as-task` to wrap the CFs into tasks where you can apply timeout/cancellation policies.

The goal is to let you write Matrix bots/apps in idiomatic-ish Clojure while Trixnity does the heavy lifting underneath.

Project Status: [Experimental](https://docs.outskirtslabs.com/open-source-vital-signs#experimental)

## Happy Path

Use [`ol.trixnity.repo`](/home/ramblurr/src/github.com/outskirtslabs/trixnity-clj/src/clj/ol/trixnity/repo.clj) to configure the built-in sqlite4clj repository, then open a client with [`ol.trixnity.client/open!`](/home/ramblurr/src/github.com/outskirtslabs/trixnity-clj/src/clj/ol/trixnity/client.clj):

```clojure
(ns my.bot
  (:require
   [ol.trixnity.client :as client]
   [ol.trixnity.event :as event]
   [ol.trixnity.repo :as repo]
   [ol.trixnity.room :as room]
   [ol.trixnity.room.message :as msg]
   [ol.trixnity.schemas :as mx]
   [ol.trixnity.timeline :as timeline])
  (:import
   (java.time Duration)))

(let [client (.get
              (client/open!
               (merge
                {::mx/homeserver-url "https://matrix.example.org"
                 ::mx/username       "bot"
                 ::mx/password       "secret"}
                (repo/sqlite4clj-config
                 {::mx/database-path "./var/trixnity.sqlite"
                  ::mx/media-path    "./var/media"}))))
      _      (.get (client/start-sync! client))
      _      (.get (client/await-running! client
                                          {::mx/timeout
                                           (Duration/ofSeconds 30)}))
      sub    (timeline/subscribe!
              client
              {::mx/decryption-timeout
               (Duration/ofSeconds 8)}
              (fn [ev]
                (when (event/text? ev)
                  (room/send! client
                              (event/room-id ev)
                              (-> (msg/text "pong")
                                  (msg/reply-to ev))
                              {::mx/timeout
                               (Duration/ofSeconds 5)}))))]
  {:client client
   :subscription sub})
```

The one-shot operations return cancelable `CompletableFuture` values. You can
block with `.get`, compose them with your preferred JVM async library, or call
`.cancel` directly.

If you want a different repository implementation, construct a `MatrixClient`
yourself and pass it to `client/open!` as `::mx/client`.


## License: Apache License 2.0

Copyright © 2026 Casey Link

Distributed under the [Apache-2.0](https://spdx.org/licenses/Apache-2.0.html) just like Trixnity itself.

[trixnity]: https://trixnity.connect2x.de/
[trixnity-code]: https://gitlab.com/connect2x/trixnity/trixnity

[quiescent]: https://github.com/multiplyco/quiescent
