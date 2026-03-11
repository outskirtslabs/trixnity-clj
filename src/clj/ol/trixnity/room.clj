(ns ol.trixnity.room
  (:require
   [ol.trixnity.event :as event]
   [ol.trixnity.interop :as interop]
   [ol.trixnity.schemas :as mx]))

(set! *warn-on-reflection* true)

(def ^:private schema-registry
  (mx/registry {}))

(defn- one-shot-request [client opts]
  (cond-> {::mx/client client}
    (::mx/timeout opts) (assoc ::mx/timeout (::mx/timeout opts))))

(defn create!
  "Creates a room.

  The public opts shape intentionally stays close to upstream:

  `{::mx/room-name \"Ops Bot\"}`"
  [client opts]
  (let [opts (mx/validate! schema-registry ::mx/CreateRoomOpts opts)]
    (interop/create-room
     {::mx/client    client
      ::mx/room-name (::mx/room-name opts)})))

(defn invite!
  "Invites `user-id` to `room-id`.

  Supported opts:

  `{::mx/timeout java.time.Duration}`"
  ([client room-id user-id]
   (invite! client room-id user-id {}))
  ([client room-id user-id opts]
   (let [opts (mx/validate! schema-registry ::mx/InviteOpts opts)]
     (interop/invite-user
      (merge
       {::mx/client  client
        ::mx/room-id room-id
        ::mx/user-id user-id}
       (select-keys (one-shot-request client opts)
                    [::mx/timeout]))))))

(defn send!
  "Sends a message-spec map produced by [[ol.trixnity.room.message/text]].

  The `message` map adapts directly to Trixnity's `MessageBuilder.text` and
  `MessageBuilder.reply` DSL."
  ([client room-id message]
   (send! client room-id message {}))
  ([client room-id message opts]
   (let [message (mx/validate! schema-registry
                               ::mx/MessageSpec
                               message)
         opts    (mx/validate! schema-registry ::mx/SendOpts opts)]
     (interop/send-message
      (merge
       {::mx/client  client
        ::mx/room-id room-id
        ::mx/message message}
       (select-keys (one-shot-request client opts)
                    [::mx/timeout]))))))

(defn react!
  "Sends a reaction to `ev` using a normalized event map."
  ([client room-id ev key]
   (react! client room-id ev key {}))
  ([client room-id ev key opts]
   (let [opts (mx/validate! schema-registry ::mx/SendOpts opts)]
     (interop/send-reaction
      (merge
       {::mx/client   client
        ::mx/room-id  room-id
        ::mx/event-id (event/event-id ev)
        ::mx/key      key}
       (select-keys (one-shot-request client opts)
                    [::mx/timeout]))))))
