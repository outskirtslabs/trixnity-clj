(ns ol.trixnity.schemas
  (:require
   [com.fulcrologic.guardrails.malli.registry :as gr.reg]
   [malli.error :as me]
   [malli.core :as m]
   [malli.util :as mu])
  (:import
   (net.folivo.trixnity.client MatrixClient)
   (org.jetbrains.exposed.sql Database)
   (ol.trixnity.bridge TimelinePumpHandle)))

(set! *warn-on-reflection* true)

(defn schemas
  [_]
  {::homeserver-url                    :string
   ::username                          :string
   ::password                          :string
   ::database                          [:fn #(instance? Database %)]
   ::media-path                        :string
   ::client                            [:fn #(instance? MatrixClient %)]
   ::timeline-pump                     [:fn #(instance? TimelinePumpHandle %)]
   ::room-name                         :string
   ::room-id                           :string
   ::user-id                           :string
   ::event-id                          :string
   ::body                              :string
   ::key                               :string
   ::on-event                          [:fn ifn?]
   ::LoginRequest
   [:map
    [::homeserver-url ::homeserver-url]
    [::username ::username]
    [::password ::password]
    [::database ::database]
    [::media-path ::media-path]]
   ::FromStoreRequest
   [:map
    [::database ::database]
    [::media-path ::media-path]]
   ::StartSyncRequest
   [:map
    [::client ::client]]
   ::CreateRoomRequest
   [:map
    [::client ::client]
    [::room-name ::room-name]]
   ::InviteUserRequest
   [:map
    [::client ::client]
    [::room-id ::room-id]
    [::user-id ::user-id]]
   ::SendTextReplyRequest
   [:map
    [::client ::client]
    [::room-id ::room-id]
    [::event-id ::event-id]
    [::body ::body]]
   ::SendReactionRequest
   [:map
    [::client ::client]
    [::room-id ::room-id]
    [::event-id ::event-id]
    [::key ::key]]
   ::StartTimelinePumpRequest
   [:map
    [::client ::client]
    [::on-event ::on-event]]
   ::StopTimelinePumpRequest
   [:map
    [::client ::client]
    [::timeline-pump ::timeline-pump]]})

(defn registry
  [opts]
  (merge (m/default-schemas)
         (mu/schemas)
         (schemas opts)))

(gr.reg/merge-schemas! (merge (mu/schemas)
                              (schemas {})))

(defn validate!
  [schema-registry schema-id data]
  (let [schema (m/schema schema-id {:registry schema-registry})]
    (if (m/validate schema data)
      data
      (throw
       (ex-info "Schema validation failed"
                {:schema schema-id
                 :errors (me/humanize (m/explain schema data))
                 :data   data})))))
