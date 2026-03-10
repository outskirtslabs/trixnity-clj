(ns ol.trixnity.interop
  (:require
   [com.fulcrologic.guardrails.malli.core :refer [=> >defn]]
   [ol.trixnity.schemas :as schemas])
  (:import
   (ol.trixnity.bridge
    ClientBridge
    CreateRoomRequest
    EventBridge
    InviteUserRequest
    RoomBridge
    SendReactionRequest
    SendTextReplyRequest
    StartTimelinePumpRequest
    StopTimelinePumpRequest
    TimelinePumpHandle)))

(set! *warn-on-reflection* true)

(>defn login-blocking
       [request]
       [::schemas/LoginRequest => :some]
       (ClientBridge/loginBlocking
        (schemas/validate! (schemas/registry {}) ::schemas/LoginRequest request)))

(>defn from-store-blocking
       [request]
       [::schemas/FromStoreRequest => [:maybe :some]]
       (ClientBridge/fromStoreBlocking
        (schemas/validate! (schemas/registry {}) ::schemas/FromStoreRequest request)))

(>defn start-sync-blocking
       [request]
       [::schemas/StartSyncRequest => :nil]
       (ClientBridge/startSyncBlocking
        (schemas/validate! (schemas/registry {}) ::schemas/StartSyncRequest request)))

(defn create-room-blocking
  [^CreateRoomRequest request]
  (RoomBridge/createRoomBlocking request))

(defn invite-user-blocking
  [^InviteUserRequest request]
  (RoomBridge/inviteUserBlocking request))

(defn send-text-reply-blocking
  [^SendTextReplyRequest request]
  (RoomBridge/sendTextReplyBlocking request))

(defn send-reaction-blocking
  [^SendReactionRequest request]
  (RoomBridge/sendReactionBlocking request))

(defn start-timeline-pump
  ^TimelinePumpHandle
  [^StartTimelinePumpRequest request]
  (EventBridge/startTimelinePump request))

(defn stop-timeline-pump
  [^StopTimelinePumpRequest request]
  (EventBridge/stopTimelinePump request))
