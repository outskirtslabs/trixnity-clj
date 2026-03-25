(ns basic-bot.main-test
  (:require
   [clojure.test :refer [deftest is run-tests testing]]
   [missionary.core :as m]
   [ol.trixnity.client :as client]
   [ol.trixnity.repo :as repo]
   [ol.trixnity.room :as room]
   [ol.trixnity.schemas :as mx])
  (:import
   [java.time Duration]))

(defn -main [& _]
  (let [{:keys [fail error]} (run-tests 'basic-bot.main-test)]
    (when (pos? (+ fail error))
      (System/exit 1))))

(defn- task-of [value]
  (m/sp value))

(defn- single-value-flow [value]
  (m/observe
   (fn [emit]
     (future
       (emit value))
     (constantly nil))))

(defn- resolve-example-var [var-sym]
  (try
    (requiring-resolve (symbol "basic-bot.main" (name var-sym)))
    (catch Throwable _
      nil)))

(defn- event [body]
  {::mx/type     "m.room.message"
   ::mx/room-id  "!room:example.org"
   ::mx/event-id "$event"
   ::mx/sender   "@alice:example.org"
   ::mx/body     body})

(deftest decide-action-enforces-command-and-passive-precedence-test
  (let [decide-action-var (resolve-example-var 'decide-action)]
    (is (some? decide-action-var)
        "basic-bot.main/decide-action is missing")
    (when decide-action-var
      (let [decide-action (var-get decide-action-var)
            ctx           {:bot-user-id  "@bot:example.org"
                           :topic-picker (constantly "Ask about bridge design.")}]
        (testing "commands take precedence and echo preserves the exact payload"
          (is (= {:kind     :send-message
                  :body     "Commands: !help, !ping, !echo <text>, !topic"
                  :reply-to (event "!help")}
                 (decide-action ctx (event "!help"))))
          (is (= {:kind     :send-message
                  :body     "pong"
                  :reply-to (event "!ping thanks")}
                 (decide-action ctx (event "!ping thanks"))))
          (is (= {:kind     :send-message
                  :body     "hello there"
                  :reply-to (event "!echo hello there")}
                 (decide-action ctx (event "!echo hello there"))))
          (is (= {:kind     :send-message
                  :body     "  "
                  :reply-to (event "!echo   ")}
                 (decide-action ctx (event "!echo   "))))
          (is (= {:kind     :send-message
                  :body     "Usage: !echo <text>"
                  :reply-to (event "!echo")}
                 (decide-action ctx (event "!echo"))))
          (is (= {:kind     :send-message
                  :body     "Ask about bridge design."
                  :reply-to (event "!topic")}
                 (decide-action ctx (event "!topic")))))
        (testing "thanks beats greeting, greetings use display name, and self events are ignored"
          (is (= {:kind     :send-reaction
                  :key      "❤️"
                  :event-id "$event"}
                 (decide-action ctx
                                (assoc (event "Hey thanks for the help")
                                       ::mx/sender-display-name "Alice"))))
          (is (= {:kind     :send-message
                  :body     "Hi, Alice!"
                  :reply-to (assoc (event "Hello there")
                                   ::mx/sender-display-name "Alice")}
                 (decide-action ctx
                                (assoc (event "Hello there")
                                       ::mx/sender-display-name "Alice"))))
          (is (= {:kind     :send-message
                  :body     "Hi, @alice:example.org!"
                  :reply-to (event "hey")}
                 (decide-action ctx (event "hey"))))
          (is (nil? (decide-action ctx
                                   (assoc (event "hello")
                                          ::mx/sender "@bot:example.org")))))))))

(deftest next-invite-joins-filters-and-deduplicates-invite-snapshots-test
  (let [next-invite-joins-var (resolve-example-var 'next-invite-joins)]
    (is (some? next-invite-joins-var)
        "basic-bot.main/next-invite-joins is missing")
    (when next-invite-joins-var
      (let [next-invite-joins (var-get next-invite-joins-var)
            first-pass        (next-invite-joins
                               #{}
                               [{::mx/room-id    "!invite:example.org"
                                 ::mx/membership :invite}
                                {::mx/room-id    "!joined:example.org"
                                 ::mx/membership :join}])
            second-pass       (next-invite-joins
                               (:joining-room-ids first-pass)
                               [{::mx/room-id    "!invite:example.org"
                                 ::mx/membership :invite}])
            third-pass        (next-invite-joins
                               (:joining-room-ids second-pass)
                               [{::mx/room-id    "!invite:example.org"
                                 ::mx/membership :join}])]
        (is (= {:joining-room-ids #{"!invite:example.org"}
                :room-ids-to-join ["!invite:example.org"]}
               first-pass))
        (is (= {:joining-room-ids #{"!invite:example.org"}
                :room-ids-to-join []}
               second-pass))
        (is (= {:joining-room-ids #{}
                :room-ids-to-join []}
               third-pass))))))

(deftest run-bot-wires-the-supported-lifecycle-test
  (let [run-bot!-var             (resolve-example-var 'run-bot!)
        load-env-var             (resolve-example-var 'load-env)
        ensure-data-dir!-var     (resolve-example-var 'ensure-data-dir!)
        start-invite-loop!-var   (resolve-example-var 'start-invite-loop!)
        start-timeline-loop!-var (resolve-example-var 'start-timeline-loop!)]
    (is (some? run-bot!-var)
        "basic-bot.main/run-bot! is missing")
    (is (some? load-env-var)
        "basic-bot.main/load-env is missing")
    (is (some? ensure-data-dir!-var)
        "basic-bot.main/ensure-data-dir! is missing")
    (is (some? start-invite-loop!-var)
        "basic-bot.main/start-invite-loop! is missing")
    (is (some? start-timeline-loop!-var)
        "basic-bot.main/start-timeline-loop! is missing")
    (when (every? some?
                  [run-bot!-var
                   load-env-var
                   ensure-data-dir!-var
                   start-invite-loop!-var
                   start-timeline-loop!-var])
      (let [calls (atom [])]
        (with-redefs [repo/sqlite4clj-config
                      (fn [cfg]
                        (swap! calls conj [:repo-config cfg])
                        {:repo-config cfg})

                      client/open
                      (fn [cfg]
                        (swap! calls conj [:open cfg])
                        (task-of :client-handle))

                      client/start-sync
                      (fn [opened-client]
                        (swap! calls conj [:start-sync opened-client])
                        (task-of nil))

                      client/await-running
                      (fn [opened-client opts]
                        (swap! calls conj [:await-running opened-client opts])
                        (task-of nil))

                      client/current-user-id
                      (fn [opened-client]
                        (swap! calls conj [:current-user-id opened-client])
                        "@bot:example.org")]
          (with-redefs-fn
            {load-env-var
             (fn []
               {::mx/homeserver-url "https://matrix.example.org"
                ::mx/user-id        "@bot:example.org"
                ::mx/password       "secret"})

             ensure-data-dir!-var
             (fn [path]
               (swap! calls conj [:ensure-data-dir (str path)])
               path)

             start-invite-loop!-var
             (fn [opened-client]
               (swap! calls conj [:start-invite-loop opened-client])
               :invite-loop)

             start-timeline-loop!-var
             (fn [opened-client bot-user-id]
               (swap! calls conj [:start-timeline-loop opened-client bot-user-id])
               :timeline-loop)}
            #(let [runtime ((var-get run-bot!-var))]
               (is (= {:client        :client-handle
                       :bot-user-id   "@bot:example.org"
                       :invite-loop   :invite-loop
                       :timeline-loop :timeline-loop}
                      runtime))
               (is (some #{[:ensure-data-dir "./dev-data/basic-bot"]} @calls))
               (is (some #{[:repo-config
                            {:database-path "./dev-data/basic-bot/trixnity.sqlite"
                             :media-path    "./dev-data/basic-bot/media"}]}
                         @calls))
               (is (some #{[:open
                            {::mx/homeserver-url                                    "https://matrix.example.org"
                             ::mx/user-id                                           "@bot:example.org"
                             ::mx/password                                          "secret"
                             :repo-config
                             {:database-path "./dev-data/basic-bot/trixnity.sqlite"
                              :media-path    "./dev-data/basic-bot/media"}}]}
                         @calls))
               (is (some #{[:await-running
                            :client-handle
                            {::mx/timeout (Duration/ofSeconds 30)}]}
                         @calls))
               (is (some #{[:start-invite-loop :client-handle]} @calls))
               (is (some #{[:start-timeline-loop
                            :client-handle
                            "@bot:example.org"]}
                         @calls)))))))))

(deftest background-loops-run-on-virtual-threads-test
  (let [start-invite-loop!-var   (resolve-example-var 'start-invite-loop!)
        start-timeline-loop!-var (resolve-example-var 'start-timeline-loop!)]
    (is (some? start-invite-loop!-var)
        "basic-bot.main/start-invite-loop! is missing")
    (is (some? start-timeline-loop!-var)
        "basic-bot.main/start-timeline-loop! is missing")
    (when (and start-invite-loop!-var start-timeline-loop!-var)
      (with-redefs [room/get-all-flat
                    (fn [_]
                      (single-value-flow []))

                    room/get-timeline-events-from-now-on
                    (fn [_ _]
                      (single-value-flow
                       {::mx/type     "m.room.message"
                        ::mx/room-id  "!room:example.org"
                        ::mx/event-id "$event"
                        ::mx/sender   "@bot:example.org"
                        ::mx/body     "hello"}))]
        (let [invite-thread   ((var-get start-invite-loop!-var) :client-handle)
              timeline-thread ((var-get start-timeline-loop!-var)
                               :client-handle
                               "@bot:example.org")]
          (.join ^Thread invite-thread 1000)
          (.join ^Thread timeline-thread 1000)
          (is (instance? Thread invite-thread))
          (is (instance? Thread timeline-thread))
          (is (.isVirtual ^Thread invite-thread))
          (is (.isVirtual ^Thread timeline-thread)))))))
