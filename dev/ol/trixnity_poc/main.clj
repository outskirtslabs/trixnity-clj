(ns ol.trixnity-poc.main
  (:require
   [babashka.http-client :as http]
   [clojure.string :as str]
   [ol.trixnity-poc.bot-logic :as bot-logic]
   [ol.trixnity-poc.config :as config]
   [ol.trixnity-poc.invite-error :as invite-error]
   [ol.trixnity-poc.room-state :as room-state]
   [ol.trixnity-poc.synapse-register :as synapse-register])
  (:import
   (java.nio.file Files)
   (kotlin Result ResultKt Unit)
   (kotlin.coroutines EmptyCoroutineContext)
   (kotlin.jvm.functions Function1 Function2)
   (kotlin.time DurationKt DurationUnit)
   (kotlinx.coroutines BuildersKt Dispatchers)
   (kotlinx.coroutines.flow FlowKt)
   (net.folivo.trixnity.client DefaultModulesKt MatrixClient MatrixClientKt)
   (net.folivo.trixnity.client.media.okio OkioMediaStoreKt)
   (net.folivo.trixnity.client.room.message ReactKt ReplyKt TextKt)
   (net.folivo.trixnity.client.store TimelineEventExtensionsKt)
   (net.folivo.trixnity.client.store.repository.exposed
    CreateExposedRepositoriesModuleKt)
   (net.folivo.trixnity.clientserverapi.client SyncState)
   (net.folivo.trixnity.clientserverapi.model.authentication
    IdentifierType$User
    LoginType$Password)
   (net.folivo.trixnity.clientserverapi.model.rooms DirectoryVisibility)
   (net.folivo.trixnity.core.model EventId RoomId UserId)
   (net.folivo.trixnity.core.model.events InitialStateEvent)
   (net.folivo.trixnity.core.model.events.m ReactionEventContent)
   (net.folivo.trixnity.core.model.events.m.room
    EncryptionEventContent
    RoomMessageEventContent$TextBased$Text)
   (org.jetbrains.exposed.sql Database)))

(defn- run-blocking [f]
  (BuildersKt/runBlocking
   EmptyCoroutineContext/INSTANCE
   (reify Function2
     (invoke [_ scope cont]
       (f scope cont)))))

(defn- kotlin-result->value [^Result result]
  (let [unboxed
        (clojure.lang.Reflector/invokeInstanceMethod result
                                                     "unbox-impl"
                                                     (object-array 0))]
    (ResultKt/throwOnFailure unboxed)
    unboxed))

(defn- create-dirs! [^java.nio.file.Path path]
  (Files/createDirectories path
                           (make-array java.nio.file.attribute.FileAttribute
                                       0))
  path)

(defn- ensure-local-paths! [cfg]
  (create-dirs! (:media-path cfg))
  (some-> (:database-path cfg) (.getParent) create-dirs!)
  (some-> (:room-id-file cfg) (.getParent) create-dirs!))

(defn- create-repositories-module [cfg]
  (let [database-url (str "jdbc:h2:file:"
                          (-> (:database-path cfg)
                              (.toAbsolutePath)
                              (str))
                          ";DB_CLOSE_DELAY=-1;")
        db           (org.jetbrains.exposed.sql.Database$Companion/connect$default
                      Database/Companion
                      database-url
                      nil
                      nil
                      nil
                      nil
                      nil
                      nil
                      nil
                      254
                      nil)]
    (run-blocking
     (fn [_ cont]
       (CreateExposedRepositoriesModuleKt/createExposedRepositoriesModule db
                                                                          cont)))))

(defn- create-media-module [cfg]
  (let [base-path (.get okio.Path/Companion
                        (-> (:media-path cfg)
                            (.toAbsolutePath)
                            (str)))]
    (OkioMediaStoreKt/createOkioMediaStoreModule$default
     base-path
     nil
     nil
     6
     nil)))

(defn- from-store-client [repositories-module media-module]
  (let [result
        (run-blocking
         (fn [_ cont]
           (MatrixClientKt/fromStore MatrixClient/Companion
                                     repositories-module
                                     media-module
                                     nil
                                     (Dispatchers/getDefault)
                                     (reify Function1
                                       (invoke [_ _cfg]
                                         Unit/INSTANCE))
                                     cont)))]
    (kotlin-result->value result)))

(defn- login-client [cfg repositories-module media-module]
  (let [result
        (run-blocking
         (fn [_ cont]
           (clojure.lang.Reflector/invokeStaticMethod
            "net.folivo.trixnity.client.MatrixClientKt"
            "login"
            (object-array
             [MatrixClient/Companion
              (:homeserver-url cfg)
              (IdentifierType$User. (:username cfg))
              (:password cfg)
              nil
              LoginType$Password/INSTANCE
              nil
              nil
              repositories-module
              media-module
              (Dispatchers/getDefault)
              (reify Function1
                (invoke [_ _cfg]
                  Unit/INSTANCE))
              cont]))))]
    (kotlin-result->value result)))

(defn- json-escape [s]
  (str/escape (str s)
              {\\       "\\\\"
               \"       "\\\""
               \newline "\\n"
               \return  "\\r"
               \tab     "\\t"}))

(defn- maybe-public-register! [cfg]
  (when (and (:try-register cfg)
             (nil? (:registration-shared-secret cfg)))
    (let [endpoint (str (str/replace (config/url->string (:homeserver-url cfg))
                                     #"/$"
                                     "")
                        "/_matrix/client/v3/register")
          body     (str "{"
                        "\"username\":\"" (json-escape (:username cfg)) "\""
                        ",\"password\":\"" (json-escape (:password cfg)) "\""
                        ",\"auth\":{\"type\":\"m.login.dummy\"}"
                        "}")
          resp     (http/post endpoint
                              {:throw   false
                               :headers {:content-type "application/json"}
                               :body    body})]
      (if (<= 200 (:status resp) 299)
        (println "Public registration completed for" (:username cfg))
        (println "Public registration failed, falling back to login. status="
                 (:status resp))))))

(defn- login-or-register! [cfg repositories-module media-module]
  (if-let [shared-secret (:registration-shared-secret cfg)]
    (do
      (try
        (synapse-register/register!
         {:base-url      (config/url->string (:homeserver-url cfg))
          :username      (:username cfg)
          :password      (:password cfg)
          :shared-secret shared-secret
          :admin?        (:bot-admin cfg)})
        (println "Shared-secret registration completed for" (:username cfg))
        (catch Throwable t
          (println "Shared-secret registration failed, will continue:"
                   (ex-message t))))
      (login-client cfg repositories-module media-module))
    (do
      (maybe-public-register! cfg)
      (login-client cfg repositories-module media-module))))

(defn- start-sync! [client]
  (run-blocking
   (fn [_ cont]
     (clojure.lang.Reflector/invokeInstanceMethod client
                                                  "startSync"
                                                  (object-array [nil cont]))))
  (loop [retries 0]
    (let [state (.getValue (.getSyncState client))]
      (cond
        (= state SyncState/RUNNING)
        true

        (> retries 240)
        (throw (ex-info "sync did not reach RUNNING state"
                        {:state state}))

        :else
        (do
          (Thread/sleep 250)
          (recur (inc retries)))))))

(defn- create-room! [client room-name]
  (let [room-api      (-> client .getApi .getRoom)
        initial-state (doto (java.util.ArrayList.)
                        (.add (InitialStateEvent.
                               (EncryptionEventContent.)
                               "")))
        create-result (run-blocking
                       (fn [_ cont]
                         (clojure.lang.Reflector/invokeInstanceMethod
                          room-api
                          "createRoom-5dDjBWM"
                          (object-array
                           [DirectoryVisibility/PRIVATE
                            nil
                            room-name
                            nil
                            nil
                            nil
                            nil
                            nil
                            initial-state
                            nil
                            nil
                            nil
                            nil
                            cont]))))]
    (kotlin-result->value create-result)))

(defn- invite-user! [client room-id user-id]
  (let [room-api      (-> client .getApi .getRoom)
        invite-result (run-blocking
                       (fn [_ cont]
                         (clojure.lang.Reflector/invokeInstanceMethod
                          room-api
                          "inviteUser-yxL6bBk"
                          (object-array [room-id user-id nil nil cont]))))]
    (try
      (kotlin-result->value invite-result)
      (println "Invited user:" user-id)
      (catch Throwable t
        (if (invite-error/already-in-room-invite-failure? t)
          (println "Invite skipped:" user-id "is already in the room")
          (println "Invite failed for" user-id ":" (ex-message t)))))))

(defn- timeline-event-content [timeline-event]
  (let [content-result
        (clojure.lang.Reflector/invokeInstanceMethod timeline-event
                                                     "getContent-xLWZpok"
                                                     (object-array 0))]
    (when content-result
      (try
        (kotlin-result->value content-result)
        (catch Throwable _
          nil)))))

(defn- send-text-reply! [room-service room-id timeline-event body]
  (run-blocking
   (fn [_ cont]
     (clojure.lang.Reflector/invokeStaticMethod
      "net.folivo.trixnity.client.room.RoomService$DefaultImpls"
      "sendMessage$default"
      (object-array
       [room-service
        room-id
        true
        (reify Function2
          (invoke [_ message-builder _]
            (TextKt/text message-builder body nil nil)
            (run-blocking
             (fn [_ reply-cont]
               (ReplyKt/reply message-builder timeline-event reply-cont)))
            Unit/INSTANCE))
        cont
        0
        nil])))))

(defn- send-reaction! [room-service room-id ^EventId event-id key]
  (run-blocking
   (fn [_ cont]
     (clojure.lang.Reflector/invokeStaticMethod
      "net.folivo.trixnity.client.room.RoomService$DefaultImpls"
      "sendMessage$default"
      (object-array
       [room-service
        room-id
        true
        (reify Function2
          (invoke [_ message-builder _]
            (ReactKt/react message-builder event-id key)
            Unit/INSTANCE))
        cont
        0
        nil])))))

(defn- handle-timeline-event! [client room-service target-room-id timeline-event]
  (let [room-id (TimelineEventExtensionsKt/getRoomId timeline-event)]
    (when (= room-id target-room-id)
      (let [sender (TimelineEventExtensionsKt/getSender timeline-event)]
        (when (bot-logic/should-handle-sender? sender (.getUserId client))
          (when-let [content (timeline-event-content timeline-event)]
            (cond
              (instance? RoomMessageEventContent$TextBased$Text content)
              (let [body     (.getBody ^RoomMessageEventContent$TextBased$Text content)
                    mirrored (bot-logic/mirrored-body body)]
                (send-text-reply! room-service room-id timeline-event mirrored))

              (instance? ReactionEventContent content)
              (when-let [{:keys [event-id key]}
                         (bot-logic/reaction-to-mirror content)]
                (send-reaction! room-service room-id event-id key))

              :else nil)))))))

(defn- collect-timeline! [client room-id]
  (let [room-service    (DefaultModulesKt/getRoom client)
        decryption-time (DurationKt/toDuration 8 DurationUnit/SECONDS)
        timeline-events (clojure.lang.Reflector/invokeInstanceMethod
                         room-service
                         "getTimelineEventsFromNowOn-VtjQ1oo"
                         (object-array [decryption-time 4]))]
    (run-blocking
     (fn [_ cont]
       (FlowKt/collect
        timeline-events
        (reify Function2
          (invoke [_ timeline-event _]
            (try
              (handle-timeline-event! client
                                      room-service
                                      room-id
                                      timeline-event)
              (catch Throwable t
                (println "timeline handling error:" (ex-message t))))
            Unit/INSTANCE))
        cont)))))

(defn run-poc! []
  (let [cfg                 (config/load-config)
        _                   (ensure-local-paths! cfg)
        repositories-module (create-repositories-module cfg)
        media-module        (create-media-module cfg)
        matrix-client       (or (from-store-client repositories-module media-module)
                                (login-or-register! cfg
                                                    repositories-module
                                                    media-module))]
    (start-sync! matrix-client)

    (let [stored-room-id (room-state/load-room-id (:room-id-file cfg))
          room-id        (or stored-room-id
                             (let [created-room-id (create-room! matrix-client
                                                                 (:room-name cfg))]
                               (room-state/save-room-id! (:room-id-file cfg)
                                                         created-room-id)
                               (println "Created room and persisted id to"
                                        (:room-id-file cfg))
                               created-room-id))]
      (when-let [invite-user ^UserId (:invite-user cfg)]
        (invite-user! matrix-client room-id invite-user))

      (when stored-room-id
        (println "Reusing room from state file:" room-id))
      (println "Room name:" (:room-name cfg))
      (println "Room id:" room-id)
      (println "Bot user:" (.getUserId matrix-client))

      (collect-timeline! matrix-client room-id)
      {:config  cfg
       :room-id room-id
       :client  matrix-client})))

(defn -main [& _]
  (run-poc!))
