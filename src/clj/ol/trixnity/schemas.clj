(ns ol.trixnity.schemas
  "Malli schema registry for ol.trixnity

  Public namespaces validate arguments and normalize bridge-shaped data through
  the schemas defined here before crossing into
  [[ol.trixnity.internal.bridge]].

  The registry includes:

  - public request and options maps for client, room, notification, and key
    operations
  - normalized event, profile, room, notification, verification, and trust
    data shapes
  - helper functions for building a registry and enforcing schema validation

  Most callers use these namespaced keys indirectly through the higher-level
  public APIs. Reach for this namespace directly when constructing config or
  payload maps by hand."
  (:require
   [clojure.string :as str]
   [com.fulcrologic.guardrails.malli.registry :as gr.reg]
   [malli.core :as m]
   [malli.error :as me]
   [malli.util :as mu])
  (:import
   [de.connect2x.trixnity.client MatrixClient]
   [de.connect2x.trixnity.clientserverapi.model.authentication.oauth2 ServerMetadata]
   [de.connect2x.trixnity.clientserverapi.model.media GetMediaConfig$Response]
   [de.connect2x.trixnity.clientserverapi.model.server GetCapabilities$Response
    GetVersions$Response]
   [de.connect2x.trixnity.clientserverapi.model.sync Sync$Response]
   [de.connect2x.trixnity.core.model.events GlobalAccountDataEventContent
    RoomAccountDataEventContent RoomEventContent StateEventContent]
   [de.connect2x.trixnity.core.model.keys RoomKeyBackupAuthData]
   [java.io Closeable]
   [java.time Duration]))

(set! *warn-on-reflection* true)

(defn- class-assignable-to?
  [^Class expected value]
  (and (instance? Class value)
       (.isAssignableFrom expected ^Class value)))

(defn- non-blank-string?
  [value]
  (and (string? value)
       (not (str/blank? value))))

(defn- matrix-id?
  [sigil value]
  (and (non-blank-string? value)
       (str/starts-with? value sigil)
       (str/includes? value ":")))

(defn- message-kind-schema
  [kind]
  [:enum kind (name kind) (str kind)])

(defn schemas
  "Returns the project Malli schema map for `opts`."
  [_]
  {::homeserver-url                                :string
   ::password                                      :string
   ::database-path                                 :string
   ::media-path                                    :string
   ::client                                        [:fn #(instance? MatrixClient %)]
   ::room-name                                     :string
   ::topic                                         :string
   ::room-id                                       :string
   ::room-alias-id                                 [:fn #(matrix-id? "#" %)]
   ::room-id-or-alias                              [:fn #(or (matrix-id? "!" %)
                                                             (matrix-id? "#" %))]
   ::membership                                    [:and
                                                    :keyword
                                                    [:fn #(= (name %)
                                                             (str/lower-case
                                                              (name %)))]]
   ::user-id                                       :string
   ::invite                                        [:vector ::user-id]
   ::preset                                        [:enum :private-chat :public-chat :trusted-private-chat]
   ::visibility                                    [:enum :public :private]
   ::users                                         [:set ::user-id]
   ::event-id                                      :string
   ::version                                       :string
   ::state-key                                     :string
   ::transaction-id                                :string
   ::device-name                                   :string
   ::device-id                                     :string
   ::body                                          :string
   ::source-path                                   [:fn non-blank-string?]
   ::file-name                                     [:fn non-blank-string?]
   ::mime-type                                     [:fn non-blank-string?]
   ::size-bytes                                    nat-int?
   ::height                                        pos-int?
   ::width                                         pos-int?
   ::key                                           :string
   ::display-name                                  :string
   ::avatar-url                                    :string
   ::time-zone                                     :string
   ::duration                                      [:fn #(instance? Duration %)]
   ::timeout                                       ::duration
   ::decryption-timeout                            ::duration
   ::wait                                          :boolean
   ::force                                         :boolean
   ::limit                                         pos-int?
   ::fetch-timeout                                 ::duration
   ::fetch-size                                    pos-int?
   ::allow-replace-content                         :boolean
   ::min-size                                      nat-int?
   ::max-size                                      nat-int?
   ::sync-response-buffer-size                     nat-int?
   ::direction                                     [:enum :backwards :forwards]
   ::response                                      [:fn #(instance? Sync$Response %)]
   ::room-event-content-class                      [:fn #(class-assignable-to? RoomEventContent %)]
   ::state-event-content-class                     [:fn #(class-assignable-to? StateEventContent %)]
   ::global-account-data-event-content-class
   [:fn #(class-assignable-to? GlobalAccountDataEventContent %)]
   ::room-account-data-event-content-class
   [:fn #(class-assignable-to? RoomAccountDataEventContent %)]
   ::room-event-content                            [:fn #(instance? RoomEventContent %)]
   ::closeable                                     [:fn #(instance? Closeable %)]
   ::kind                                          [:or :keyword :string]
   ::id                                            :string
   ::format                                        :string
   ::formatted-body                                :string
   ::type                                          :string
   ::sender                                        :string
   ::sender-display-name                           :string
   ::is-direct                                     :boolean
   ::content                                       :any
   ::created-at                                    :string
   ::sent-at                                       :string
   ::send-error                                    :string
   ::receipt-type                                  :string
   ::name                                          :string
   ::presence                                      [:or :keyword :string]
   ::last-update                                   :string
   ::last-active                                   :string
   ::currently-active                              :boolean
   ::status-message                                :string
   ::level                                         int?
   ::verified                                      :boolean
   ::reason                                        :string
   ::dismissed                                     :boolean
   ::sort-key                                      :string
   ::actions                                       [:set :string]
   ::notification-kind                             [:or :keyword :string]
   ::notification-update-kind                      [:or :keyword :string]
   ::timestamp                                     int?
   ::their-user-id                                 :string
   ::their-device-id                               :string
   ::request-event-id                              :string
   ::methods                                       [:set [:or :keyword :string]]
   ::reasons                                       [:set [:or :keyword :string]]
   ::algorithm                                     :string
   ::raw                                           :any
   ::direct-chat-room-ids                          [:set ::room-id]
   ::direct-chat-mappings                          [:map-of ::user-id ::direct-chat-room-ids]
   ::relation-type                                 :string
   ::relation-event-id                             :string
   ::reply-to-event-id                             :string
   ::is-falling-back                               :boolean
   ::server-versions                               [:fn #(instance? GetVersions$Response %)]
   ::server-media-config                           [:fn #(instance? GetMediaConfig$Response %)]
   ::server-capabilities                           [:fn #(instance? GetCapabilities$Response %)]
   ::server-auth                                   [:fn #(instance? ServerMetadata %)]
   ::backup-auth                                   [:fn #(instance? RoomKeyBackupAuthData %)]

   ::OneShotOpts
   [:map
    [::timeout {:optional true} ::timeout]]

   ::CreateRoomOpts
   [:map
    [::room-name {:optional true} ::room-name]
    [::topic {:optional true} ::topic]
    [::invite {:optional true} ::invite]
    [::preset {:optional true} ::preset]
    [::is-direct {:optional true} ::is-direct]
    [::visibility {:optional true} ::visibility]]

   ::TimelineSubscribeOpts
   [:map
    [::decryption-timeout {:optional true} ::decryption-timeout]
    [::sync-response-buffer-size {:optional true} ::sync-response-buffer-size]]

   ::TimelineEventOpts
   [:map
    [::decryption-timeout {:optional true} ::decryption-timeout]
    [::fetch-timeout {:optional true} ::fetch-timeout]
    [::fetch-size {:optional true} ::fetch-size]
    [::allow-replace-content {:optional true} ::allow-replace-content]]

   ::TimelineEventsOpts
   [:map
    [::decryption-timeout {:optional true} ::decryption-timeout]
    [::fetch-timeout {:optional true} ::fetch-timeout]
    [::fetch-size {:optional true} ::fetch-size]
    [::allow-replace-content {:optional true} ::allow-replace-content]
    [::min-size {:optional true} ::min-size]
    [::max-size {:optional true} ::max-size]]

   ::TimelineListOpts
   ::TimelineEventOpts

   ::TimelineAroundOpts
   ::TimelineEventOpts

   ::NotificationOpts
   [:map
    [::decryption-timeout {:optional true} ::decryption-timeout]
    [::sync-response-buffer-size {:optional true} ::sync-response-buffer-size]]

   ::InviteOpts
   ::OneShotOpts

   ::JoinOpts
   ::OneShotOpts

   ::LoadMembersOpts
   [:map
    [::wait {:optional true} ::wait]]

   ::ForgetRoomOpts
   [:map
    [::force {:optional true} ::force]]

   ::FillTimelineGapsOpts
   [:map
    [::limit {:optional true} ::limit]]

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

   ::TextMessageSpec
   [:map
    [::kind (message-kind-schema :text)]
    [::body ::body]
    [::format {:optional true} ::format]
    [::formatted-body {:optional true} ::formatted-body]
    [::reply-to {:optional true} ::ReplyTarget]]

   ::EmoteMessageSpec
   [:map
    [::kind (message-kind-schema :emote)]
    [::body ::body]
    [::format {:optional true} ::format]
    [::formatted-body {:optional true} ::formatted-body]
    [::reply-to {:optional true} ::ReplyTarget]]

   ::AudioMessageSpec
   [:map
    [::kind (message-kind-schema :audio)]
    [::body ::body]
    [::source-path ::source-path]
    [::file-name {:optional true} ::file-name]
    [::mime-type {:optional true} ::mime-type]
    [::size-bytes {:optional true} ::size-bytes]
    [::duration {:optional true} ::duration]
    [::format {:optional true} ::format]
    [::formatted-body {:optional true} ::formatted-body]
    [::reply-to {:optional true} ::ReplyTarget]]

   ::ImageMessageSpec
   [:map
    [::kind (message-kind-schema :image)]
    [::body ::body]
    [::source-path ::source-path]
    [::file-name {:optional true} ::file-name]
    [::mime-type {:optional true} ::mime-type]
    [::size-bytes {:optional true} ::size-bytes]
    [::height {:optional true} ::height]
    [::width {:optional true} ::width]
    [::format {:optional true} ::format]
    [::formatted-body {:optional true} ::formatted-body]
    [::reply-to {:optional true} ::ReplyTarget]]

   ::FileMessageSpec
   [:map
    [::kind (message-kind-schema :file)]
    [::body ::body]
    [::source-path ::source-path]
    [::file-name {:optional true} ::file-name]
    [::mime-type {:optional true} ::mime-type]
    [::size-bytes {:optional true} ::size-bytes]
    [::format {:optional true} ::format]
    [::formatted-body {:optional true} ::formatted-body]
    [::reply-to {:optional true} ::ReplyTarget]]

   ::MessageSpec
   [:or
    ::TextMessageSpec
    ::EmoteMessageSpec
    ::AudioMessageSpec
    ::ImageMessageSpec
    ::FileMessageSpec]

   ::Event
   [:map
    [::type {:optional true} ::type]
    [::room-id ::room-id]
    [::event-id ::event-id]
    [::sender {:optional true} ::sender]
    [::sender-display-name {:optional true} ::sender-display-name]
    [::body {:optional true} ::body]
    [::key {:optional true} ::key]
    [::relates-to {:optional true} ::Relation]
    [::content {:optional true} ::content]
    [::raw {:optional true} ::raw]]

   ::TimelineEvent
   ::Event

   ::BridgeableTimelineEvent
   [:and
    ::TimelineEvent
    [:map
     [::raw ::raw]]]

   ::StateEvent
   [:map
    [::type {:optional true} ::type]
    [::room-id {:optional true} ::room-id]
    [::event-id {:optional true} ::event-id]
    [::sender ::sender]
    [::state-key ::state-key]
    [::content ::content]
    [::raw {:optional true} ::raw]]

   ::Room
   [:map
    [::room-id ::room-id]
    [::membership ::membership]
    [::room-name {:optional true} ::room-name]
    [::is-direct {:optional true} ::is-direct]
    [::raw {:optional true} ::raw]]

   ::Profile
   [:map
    [::display-name {:optional true} ::display-name]
    [::avatar-url {:optional true} ::avatar-url]
    [::time-zone {:optional true} ::time-zone]
    [::raw {:optional true} ::raw]]

   ::ServerData
   [:map
    [::versions {:optional true} ::server-versions]
    [::media-config {:optional true} ::server-media-config]
    [::capabilities {:optional true} ::server-capabilities]
    [::auth {:optional true} ::server-auth]
    [::raw {:optional true} ::raw]]

   ::TypingEventContent
   [:map
    [::users ::users]
    [::raw {:optional true} ::raw]]

   ::RoomOutboxMessage
   [:map
    [::room-id ::room-id]
    [::transaction-id ::transaction-id]
    [::event-id {:optional true} ::event-id]
    [::content ::content]
    [::created-at ::created-at]
    [::sent-at {:optional true} ::sent-at]
    [::send-error {:optional true} ::send-error]
    [::raw {:optional true} ::raw]]

   ::RoomUser
   [:map
    [::room-id ::room-id]
    [::user-id ::user-id]
    [::name ::name]
    [::raw {:optional true} ::raw]]

   ::RoomUserReceipt
   [:map
    [::receipt-type ::receipt-type]
    [::event-id ::event-id]
    [::raw {:optional true} ::raw]]

   ::RoomUserReceipts
   [:map
    [::room-id ::room-id]
    [::user-id ::user-id]
    [::receipts [:vector ::RoomUserReceipt]]
    [::raw {:optional true} ::raw]]

   ::PowerLevel
   [:map
    [::kind ::kind]
    [::level {:optional true} ::level]
    [::raw {:optional true} ::raw]]

   ::UserPresence
   [:map
    [::presence ::presence]
    [::last-update ::last-update]
    [::last-active {:optional true} ::last-active]
    [::currently-active {:optional true} ::currently-active]
    [::status-message {:optional true} ::status-message]
    [::raw {:optional true} ::raw]]

   ::Notification
   [:map
    [::id ::id]
    [::sort-key ::sort-key]
    [::actions ::actions]
    [::dismissed ::dismissed]
    [::notification-kind ::notification-kind]
    [::timeline-event {:optional true} ::TimelineEvent]
    [::state-event {:optional true} ::StateEvent]
    [::raw {:optional true} ::raw]]

   ::NotificationUpdate
   [:map
    [::id ::id]
    [::sort-key ::sort-key]
    [::notification-update-kind ::notification-update-kind]
    [::actions {:optional true} ::actions]
    [::content {:optional true} ::NotificationUpdateContent]
    [::raw {:optional true} ::raw]]

   ::NotificationUpdateContent
   [:map {:closed true}
    [::timeline-event {:optional true} ::TimelineEvent]
    [::state-event {:optional true} ::StateEvent]
    [::raw {:optional true} ::raw]]

   ::VerificationState
   [:map
    [::kind ::kind]
    [::raw {:optional true} ::raw]]

   ::ActiveVerification
   [:map
    [::their-user-id ::their-user-id]
    [::their-device-id {:optional true} ::their-device-id]
    [::request-event-id {:optional true} ::request-event-id]
    [::room-id {:optional true} ::room-id]
    [::transaction-id {:optional true} ::transaction-id]
    [::timestamp ::timestamp]
    [::verification-state ::VerificationState]
    [::raw {:optional true} ::raw]]

   ::SelfVerificationMethods
   [:map
    [::kind ::kind]
    [::methods {:optional true} ::methods]
    [::reasons {:optional true} ::reasons]
    [::raw {:optional true} ::raw]]

   ::TrustLevel
   [:map
    [::kind ::kind]
    [::verified {:optional true} ::verified]
    [::reason {:optional true} ::reason]
    [::raw {:optional true} ::raw]]

   ::BackupVersion
   [:map
    [::version ::version]
    [::algorithm ::algorithm]
    [::auth ::backup-auth]
    [::raw {:optional true} ::raw]]

   ::RoomsSnapshot
   [:vector ::Room]

   ::OpenClientRequest
   [:map
    [::homeserver-url ::homeserver-url]
    [::user-id ::user-id]
    [::password ::password]
    [::device-name {:optional true} ::device-name]
    [::device-id {:optional true} ::device-id]
    [::database-path ::database-path]
    [::media-path ::media-path]]})

(defn registry
  "Builds a Malli registry containing the project schemas and default schemas."
  [opts]
  (merge (m/default-schemas)
         (mu/schemas)
         (schemas opts)))

(def schema-registry
  "Default Malli registry used by public API validation."
  (registry {}))

(gr.reg/merge-schemas! (merge (mu/schemas)
                              (schemas {})))

(defn validate!
  "Validates `data` against `schema-id` and returns `data` on success.

  Throws `ExceptionInfo` with humanized Malli errors on validation failure."
  ([schema-id data]
   (validate! schema-registry schema-id data))
  ([registry schema-id data]
   (let [schema (m/schema schema-id {:registry registry})]
     (if (m/validate schema data)
       data
       (throw
        (ex-info "Schema validation failed"
                 {:schema schema-id
                  :errors (me/humanize (m/explain schema data))
                  :data   data}))))))
