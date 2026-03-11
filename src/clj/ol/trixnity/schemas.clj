(ns ol.trixnity.schemas
  (:require
   [com.fulcrologic.guardrails.malli.registry :as gr.reg]
   [malli.error :as me]
   [malli.core :as m]
   [malli.util :as mu])
  (:import
   [de.connect2x.trixnity.client MatrixClient]
   [java.io Closeable]
   [java.time Duration]))

(set! *warn-on-reflection* true)

(defn schemas
  [_]
  {::homeserver-url                                               :string
   ::username                                                     :string
   ::password                                                     :string
   ::database-path                                                :string
   ::media-path                                                   :string
   ::client                                                       [:fn #(instance? MatrixClient %)]
   ::room-name                                                    :string
   ::room-id                                                      :string
   ::user-id                                                      :string
   ::event-id                                                     :string
   ::body                                                         :string
   ::key                                                          :string
   ::message                                                      :map
   ::on-event                                                     [:fn ifn?]
   ::timeout                                                      [:fn #(instance? Duration %)]
   ::decryption-timeout                                           [:fn #(instance? Duration %)]
   ::closeable                                                    [:fn #(instance? Closeable %)]
   ::kind                                                         [:enum :text]
   ::format                                                       :string
   ::formatted-body                                               :string
   ::reply-to                                                     :map
   ::type                                                         :string
   ::sender                                                       :string
   ::relates-to                                                   :map
   ::raw                                                          :any
   ::relation-type                                                :string
   ::relation-event-id                                            :string
   ::reply-to-event-id                                            :string
   ::is-falling-back                                              :boolean

   ::OneShotOpts
   [:map
    [::timeout {:optional true} ::timeout]]

   ::CreateRoomOpts
   [:map
    [::room-name ::room-name]]

   ::TimelineSubscribeOpts
   [:map
    [::decryption-timeout {:optional true} ::decryption-timeout]]

   ::InviteOpts
   ::OneShotOpts

   ::SendOpts
   ::OneShotOpts

   ::Relation
   [:map
    [::relation-type ::relation-type]
    [::relation-event-id ::relation-event-id]
    [::key {:optional true} ::key]
    [::reply-to-event-id {:optional true} ::reply-to-event-id]
    [::is-falling-back {:optional true} ::is-falling-back]]

   ::ReplyTarget
   [:map
    [::event-id ::event-id]
    [::relates-to {:optional true} ::Relation]]

   ::MessageSpec
   [:map
    [::kind ::kind]
    [::body ::body]
    [::format {:optional true} ::format]
    [::formatted-body {:optional true} ::formatted-body]
    [::reply-to {:optional true} ::ReplyTarget]]

   ::Event
   [:map
    [::type ::type]
    [::room-id ::room-id]
    [::event-id ::event-id]
    [::sender {:optional true} ::sender]
    [::body {:optional true} ::body]
    [::key {:optional true} ::key]
    [::relates-to {:optional true} ::Relation]
    [::raw {:optional true} ::raw]]

   ::OpenClientRequest
   [:map
    [::homeserver-url ::homeserver-url]
    [::username ::username]
    [::password ::password]
    [::database-path ::database-path]
    [::media-path ::media-path]]

   ::CurrentUserIdRequest
   [:map
    [::client ::client]]

   ::SyncStateRequest
   [:map
    [::client ::client]]

   ::StartSyncRequest
   [:map
    [::client ::client]]

   ::AwaitRunningRequest
   [:map
    [::client ::client]
    [::timeout {:optional true} ::timeout]]

   ::StopSyncRequest
   [:map
    [::client ::client]]

   ::CloseClientRequest
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
    [::user-id ::user-id]
    [::timeout {:optional true} ::timeout]]

   ::SendMessageRequest
   [:map
    [::client ::client]
    [::room-id ::room-id]
    [::message ::message]
    [::timeout {:optional true} ::timeout]]

   ::SendReactionRequest
   [:map
    [::client ::client]
    [::room-id ::room-id]
    [::event-id ::event-id]
    [::key ::key]
    [::timeout {:optional true} ::timeout]]

   ::SubscribeTimelineRequest
   [:map
    [::client ::client]
    [::on-event ::on-event]
    [::decryption-timeout {:optional true} ::decryption-timeout]]})

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
