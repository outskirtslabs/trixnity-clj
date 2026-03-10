(ns ol.trixnity.interop
  (:require
   [com.fulcrologic.guardrails.malli.core :refer [=> >defn]]
   [ol.trixnity.schemas :as schemas])
  (:import
   (ol.trixnity.bridge
    ClientBridge
    EventBridge
    RoomBridge
    TimelinePumpHandle)))

(set! *warn-on-reflection* true)

(def ^:private schema-registry
  (schemas/registry {}))

(>defn login-with-password-blocking
       [request]
       [::schemas/LoginRequest => :some]
       (ClientBridge/loginWithPasswordBlocking
        (schemas/validate! schema-registry ::schemas/LoginRequest request)))

(>defn from-store-blocking
       [request]
       [::schemas/FromStoreRequest => [:maybe :some]]
       (ClientBridge/fromStoreBlocking
        (schemas/validate! schema-registry ::schemas/FromStoreRequest request)))

(>defn start-sync-blocking
       [request]
       [::schemas/StartSyncRequest => :nil]
       (ClientBridge/startSyncBlocking
        (schemas/validate! schema-registry ::schemas/StartSyncRequest request)))

(>defn create-room-blocking
       [request]
       [::schemas/CreateRoomRequest => :string]
       (RoomBridge/createRoomBlocking
        (schemas/validate! schema-registry ::schemas/CreateRoomRequest request)))

(>defn invite-user-blocking
       [request]
       [::schemas/InviteUserRequest => :nil]
       (RoomBridge/inviteUserBlocking
        (schemas/validate! schema-registry ::schemas/InviteUserRequest request)))

(>defn send-text-reply-blocking
       [request]
       [::schemas/SendTextReplyRequest => :nil]
       (RoomBridge/sendTextReplyBlocking
        (schemas/validate! schema-registry ::schemas/SendTextReplyRequest request)))

(>defn send-reaction-blocking
       [request]
       [::schemas/SendReactionRequest => :nil]
       (RoomBridge/sendReactionBlocking
        (schemas/validate! schema-registry ::schemas/SendReactionRequest request)))

(>defn start-timeline-pump
       [request]
       [::schemas/StartTimelinePumpRequest => [:fn #(instance? TimelinePumpHandle %)]]
       (EventBridge/startTimelinePump
        (schemas/validate! schema-registry ::schemas/StartTimelinePumpRequest request)))

(>defn stop-timeline-pump
       [request]
       [::schemas/StopTimelinePumpRequest => :nil]
       (EventBridge/stopTimelinePump
        (schemas/validate! schema-registry ::schemas/StopTimelinePumpRequest request)))
