# trixnity-clj

> A Clojure facade for the [Trixnity][trixnity], a Matrix SDK

## Happy Path

Use [`ol.trixnity.repo`](/home/ramblurr/src/github.com/outskirtslabs/trixnity-clj/src/clj/ol/trixnity/repo.clj) to configure the built-in sqlite4clj repository, then hand that config to [`ol.trixnity.client/start!`](/home/ramblurr/src/github.com/outskirtslabs/trixnity-clj/src/clj/ol/trixnity/client.clj):

```clojure
(ns my.bot
  (:require
   [ol.trixnity.client :as client]
   [ol.trixnity.repo :as repo]
   [ol.trixnity.schemas :as schemas]))

(def runtime
  (client/start!
   (merge
    {::schemas/homeserver-url "https://matrix.example.org"
     ::schemas/username "bot"
     ::schemas/password "secret"}
    (repo/sqlite4clj-config
     {:database-path "./var/trixnity.sqlite"
      :media-path "./var/media"}))))
```

If you want a different repository implementation, construct a `MatrixClient` yourself and pass it to `client/start!` as `::schemas/client`.


## License: Apache License 2.0

Copyright © 2026 Casey Link

Distributed under the [Apache-2.0](https://spdx.org/licenses/Apache-2.0.html) just like Trixnity itself.

[trixnity]: https://trixnity.connect2x.de/
[trixnity-code]: https://gitlab.com/connect2x/trixnity/trixnity
