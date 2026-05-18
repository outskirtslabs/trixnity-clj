(ns ol.trixnity.space
  "Matrix space creation, hierarchy, and relation helpers.

  Spaces are Matrix rooms with room type `m.space`. This namespace keeps the
  wrapper thin: optional values are passed only when callers provide them, and
  parent/child relation state is managed explicitly. Use [[ol.trixnity.room]]
  and [[ol.trixnity.user]] for generic room membership and power-level APIs."
  (:require
   [missionary.core :as m]
   [ol.trixnity.internal :as internal]
   [ol.trixnity.internal.bridge :as bridge]
   [ol.trixnity.schemas :as mx]))

(set! *warn-on-reflection* true)

(def ^:private create-space-keys
  #{::mx/room-name
    ::mx/topic
    ::mx/invite
    ::mx/preset
    ::mx/is-direct
    ::mx/visibility
    ::mx/power-levels})

(def ^:private child-content-keys
  #{::mx/via
    ::mx/order
    ::mx/suggested
    ::mx/external-url})

(defn- create-space-request [opts]
  (select-keys opts create-space-keys))

(defn- child-content [opts]
  (select-keys opts child-content-keys))

(defn create-space
  "Creates a Matrix space and returns a Missionary task resolving to its room id.

  The request uses upstream create-room defaults and only forces the Matrix room
  type to `m.space`. It does not add encryption or relation state.

  Supported opts:

  | key | description |
  |-----|-------------|
  | `::mx/room-name` | Optional space name |
  | `::mx/topic` | Optional space topic |
  | `::mx/invite` | Optional users to invite during creation |
  | `::mx/preset` | Optional upstream create-room preset |
  | `::mx/is-direct` | Optional direct-room flag passed through upstream |
  | `::mx/visibility` | Optional directory visibility |
  | `::mx/power-levels` | Optional upstream power-level content override |
  | `::mx/timeout` | Maximum time to wait for the create request |"
  ([client]
   (create-space client {}))
  ([client opts]
   (let [opts (mx/validate! ::mx/CreateSpaceOpts opts)]
     (internal/suspend-task bridge/create-space
                            client
                            (create-space-request opts)
                            (get opts ::mx/timeout)))))

(defn create-subspace
  "Creates a Matrix space and adds it as a child of `parent-space-id`.

  This composes [[create-space]] with [[set-child]]. It writes only the
  `m.space.child` event in `parent-space-id`; use [[set-parent]] separately if
  the child should also contain an `m.space.parent` event.

  Supported opts:

  | key | description |
  |-----|-------------|
  | `::mx/room-name` | Optional subspace name |
  | `::mx/topic` | Optional subspace topic |
  | `::mx/invite` | Optional users to invite during creation |
  | `::mx/preset` | Optional upstream create-room preset |
  | `::mx/is-direct` | Optional direct-room flag passed through upstream |
  | `::mx/visibility` | Optional directory visibility |
  | `::mx/power-levels` | Optional upstream power-level content override |
  | `::mx/via` | Required non-empty set of server names for the child relation |
  | `::mx/order` | Optional Matrix space child ordering string |
  | `::mx/suggested` | Optional suggested-child flag |
  | `::mx/external-url` | Optional external URL for the child relation |
  | `::mx/timeout` | Maximum time to wait for each request |"
  [client parent-space-id opts]
  (mx/validate! ::mx/room-id parent-space-id)
  (let [opts (mx/validate! ::mx/CreateSubspaceOpts opts)]
    (m/sp
     (let [subspace-id (m/? (internal/suspend-task bridge/create-space
                                                   client
                                                   (create-space-request opts)
                                                   (get opts ::mx/timeout)))]
       (m/? (internal/suspend-task bridge/set-space-child
                                   client
                                   parent-space-id
                                   subspace-id
                                   (child-content opts)
                                   (get opts ::mx/timeout)))
       subspace-id))))

(defn hierarchy
  "Returns one paginated hierarchy page for `space-id` as a Missionary task.

  Supported opts:

  | key | description |
  |-----|-------------|
  | `::mx/from` | Optional pagination token |
  | `::mx/limit` | Optional maximum rooms per page |
  | `::mx/max-depth` | Optional maximum traversal depth |
  | `::mx/suggested-only` | When true, include only suggested children |
  | `::mx/timeout` | Maximum time to wait for the hierarchy request |"
  ([client space-id]
   (hierarchy client space-id {}))
  ([client space-id opts]
   (mx/validate! ::mx/room-id space-id)
   (let [opts (mx/validate! ::mx/SpaceHierarchyOpts opts)]
     (internal/suspend-task bridge/space-hierarchy
                            client
                            space-id
                            (dissoc opts ::mx/timeout)
                            (get opts ::mx/timeout)))))

(defn get-all
  "Returns a Missionary flow of local space flows keyed by room id.

  This observes spaces already known to the client. It does not perform public
  room discovery."
  [client]
  (internal/observe-keyed-flow-map client (bridge/spaces client)))

(defn get-all-flat
  "Returns a Missionary flow of local flattened space snapshots.

  This observes spaces already known to the client. It does not perform public
  room discovery."
  [client]
  (internal/observe-flow client (bridge/spaces-flat client)))

(defn get-children
  "Returns a Missionary flow of `m.space.child` state flows keyed by child room id."
  [client space-id]
  (mx/validate! ::mx/room-id space-id)
  (internal/observe-keyed-flow-map client (bridge/space-children client space-id)))

(defn get-child
  "Returns a Missionary flow of the child relation for `child-room-id`."
  [client space-id child-room-id]
  (mx/validate! ::mx/room-id space-id)
  (mx/validate! ::mx/room-id child-room-id)
  (internal/observe-flow client (bridge/space-child client space-id child-room-id)))

(defn get-parents
  "Returns a Missionary flow of `m.space.parent` state flows keyed by parent space id."
  [client room-id]
  (mx/validate! ::mx/room-id room-id)
  (internal/observe-keyed-flow-map client (bridge/space-parents client room-id)))

(defn set-child
  "Writes an `m.space.child` event in `space-id` for `child-room-id`.

  The `content` map must include `::mx/via`. It may also include `::mx/order`,
  `::mx/suggested`, and `::mx/external-url`.

  Supported opts:

  | key | description |
  |-----|-------------|
  | `::mx/timeout` | Maximum time to wait for the state event request |"
  ([client space-id child-room-id content]
   (set-child client space-id child-room-id content {}))
  ([client space-id child-room-id content opts]
   (mx/validate! ::mx/room-id space-id)
   (mx/validate! ::mx/room-id child-room-id)
   (let [content (mx/validate! ::mx/SpaceChildContent content)
         opts    (mx/validate! ::mx/SpaceRelationOpts opts)]
     (internal/suspend-task bridge/set-space-child
                            client
                            space-id
                            child-room-id
                            content
                            (get opts ::mx/timeout)))))

(defn remove-child
  "Removes the child relation from `space-id` to `child-room-id`.

  This sends empty `m.space.child` content for the child state key, which makes
  the relation invalid under Matrix space rules.

  Supported opts:

  | key | description |
  |-----|-------------|
  | `::mx/timeout` | Maximum time to wait for the state event request |"
  ([client space-id child-room-id]
   (remove-child client space-id child-room-id {}))
  ([client space-id child-room-id opts]
   (mx/validate! ::mx/room-id space-id)
   (mx/validate! ::mx/room-id child-room-id)
   (let [opts (mx/validate! ::mx/SpaceRelationOpts opts)]
     (internal/suspend-task bridge/remove-space-child
                            client
                            space-id
                            child-room-id
                            (get opts ::mx/timeout)))))

(defn set-parent
  "Writes an `m.space.parent` event in `room-id` for `parent-space-id`.

  The `content` map must include `::mx/via`. It may also include
  `::mx/canonical` and `::mx/external-url`.

  Supported opts:

  | key | description |
  |-----|-------------|
  | `::mx/timeout` | Maximum time to wait for the state event request |"
  ([client room-id parent-space-id content]
   (set-parent client room-id parent-space-id content {}))
  ([client room-id parent-space-id content opts]
   (mx/validate! ::mx/room-id room-id)
   (mx/validate! ::mx/room-id parent-space-id)
   (let [content (mx/validate! ::mx/SpaceParentContent content)
         opts    (mx/validate! ::mx/SpaceRelationOpts opts)]
     (internal/suspend-task bridge/set-space-parent
                            client
                            room-id
                            parent-space-id
                            content
                            (get opts ::mx/timeout)))))

(defn remove-parent
  "Removes the parent relation from `room-id` to `parent-space-id`.

  This sends empty `m.space.parent` content for the parent state key, which
  makes the relation invalid under Matrix space rules.

  Supported opts:

  | key | description |
  |-----|-------------|
  | `::mx/timeout` | Maximum time to wait for the state event request |"
  ([client room-id parent-space-id]
   (remove-parent client room-id parent-space-id {}))
  ([client room-id parent-space-id opts]
   (mx/validate! ::mx/room-id room-id)
   (mx/validate! ::mx/room-id parent-space-id)
   (let [opts (mx/validate! ::mx/SpaceRelationOpts opts)]
     (internal/suspend-task bridge/remove-space-parent
                            client
                            room-id
                            parent-space-id
                            (get opts ::mx/timeout)))))
