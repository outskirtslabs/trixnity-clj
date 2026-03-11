(ns ol.trixnity.interop
  (:require
   [com.fulcrologic.guardrails.malli.core :refer [>defn]]
   [ol.trixnity.schemas :as mx])
  (:import
   [ol.trixnity.bridge ClientBridge RoomBridge TimelineBridge]))

(set! *warn-on-reflection* true)

(def ^:private schema-registry
  (mx/registry {}))

(>defn open-client
       [request]
       [::mx/OpenClientRequest => :some]
       (ClientBridge/openClient
        (mx/validate! schema-registry ::mx/OpenClientRequest request)))

(>defn current-user-id
       [request]
       [::mx/CurrentUserIdRequest => :string]
       (ClientBridge/currentUserId
        (mx/validate! schema-registry ::mx/CurrentUserIdRequest request)))

(>defn sync-state
       [request]
       [::mx/SyncStateRequest => :string]
       (ClientBridge/syncState
        (mx/validate! schema-registry ::mx/SyncStateRequest request)))

(>defn start-sync
       [request]
       [::mx/StartSyncRequest => :some]
       (ClientBridge/startSync
        (mx/validate! schema-registry ::mx/StartSyncRequest request)))

(>defn await-running
       [request]
       [::mx/AwaitRunningRequest => :some]
       (ClientBridge/awaitRunning
        (mx/validate! schema-registry ::mx/AwaitRunningRequest request)))

(>defn stop-sync
       [request]
       [::mx/StopSyncRequest => :some]
       (ClientBridge/stopSync
        (mx/validate! schema-registry ::mx/StopSyncRequest request)))

(>defn close-client
       [request]
       [::mx/CloseClientRequest => :some]
       (ClientBridge/closeClient
        (mx/validate! schema-registry ::mx/CloseClientRequest request)))

(>defn create-room
       [request]
       [::mx/CreateRoomRequest => :some]
       (RoomBridge/createRoom
        (mx/validate! schema-registry ::mx/CreateRoomRequest request)))

(>defn invite-user
       [request]
       [::mx/InviteUserRequest => :some]
       (RoomBridge/inviteUser
        (mx/validate! schema-registry ::mx/InviteUserRequest request)))

(>defn send-message
       [request]
       [::mx/SendMessageRequest => :some]
       (RoomBridge/sendMessage
        (mx/validate! schema-registry ::mx/SendMessageRequest request)))

(>defn send-reaction
       [request]
       [::mx/SendReactionRequest => :some]
       (RoomBridge/sendReaction
        (mx/validate! schema-registry ::mx/SendReactionRequest request)))

(>defn subscribe-timeline
       [request]
       [::mx/SubscribeTimelineRequest => ::mx/closeable]
       (TimelineBridge/subscribeTimeline
        (mx/validate! schema-registry
                      ::mx/SubscribeTimelineRequest
                      request)))
