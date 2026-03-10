(ns ol.trixnity.interop
  (:import
   (ol.trixnity.bridge
    ClientBridge
    CreateRoomRequest
    EventBridge
    FromStoreRequest
    InviteUserRequest
    LoginRequest
    RoomBridge
    SendReactionRequest
    SendTextReplyRequest
    StartSyncRequest
    StartTimelinePumpRequest
    StopTimelinePumpRequest
    TimelinePumpHandle)))

(defn login-blocking
  ^Object
  [^LoginRequest request]
  (ClientBridge/loginBlocking request))

(defn from-store-blocking
  ^Object
  [^FromStoreRequest request]
  (ClientBridge/fromStoreBlocking request))

(defn start-sync-blocking
  [^StartSyncRequest request]
  (ClientBridge/startSyncBlocking request))

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
