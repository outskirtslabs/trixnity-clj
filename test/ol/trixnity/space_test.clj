(ns ol.trixnity.space-test
  (:require
   [clojure.test :refer [deftest is]]
   [missionary.core :as m]
   [ol.trixnity.internal :as internal]
   [ol.trixnity.schemas :as schemas])
  (:import
   [java.io Closeable]
   [java.time Duration]))

(defn- require-space-ns []
  (try
    (require 'ol.trixnity.space :reload)
    true
    (catch java.io.FileNotFoundException _ false)))

(defn- resolve-space-var [var-sym]
  (when (require-space-ns)
    (ns-resolve 'ol.trixnity.space var-sym)))

(defn- resolve-bridge-var [var-sym]
  (ns-resolve 'ol.trixnity.internal.bridge var-sym))

(defn- realize-task [task]
  (m/? task))

(deftype StubCloseable [closed-count]
  Closeable
  (close [_]
    (swap! closed-count inc)))

(deftest space-creation-surfaces-return-missionary-tasks-test
  (let [create-space-var        (resolve-space-var 'create-space)
        create-subspace-var     (resolve-space-var 'create-subspace)
        bridge-create-space-var (resolve-bridge-var 'create-space)
        bridge-set-child-var    (resolve-bridge-var 'set-space-child)
        timeout                 (Duration/ofSeconds 5)
        create-opts             {::schemas/room-name  "Project"
                                 ::schemas/topic      "Project coordination"
                                 ::schemas/invite     ["@alice:example.org"]
                                 ::schemas/preset     :private-chat
                                 ::schemas/visibility :private}
        subspace-opts           {::schemas/room-name "Project Docs"
                                 ::schemas/topic     "Documentation"
                                 ::schemas/via       #{"example.org"}
                                 ::schemas/order     "a"
                                 ::schemas/suggested true
                                 ::schemas/timeout   timeout}
        calls                   (atom [])]
    (is (some? create-space-var)
        "ol.trixnity.space/create-space is missing")
    (is (some? create-subspace-var)
        "ol.trixnity.space/create-subspace is missing")
    (is (some? bridge-create-space-var)
        "ol.trixnity.internal.bridge/create-space is missing")
    (is (some? bridge-set-child-var)
        "ol.trixnity.internal.bridge/set-space-child is missing")
    (when (every? some? [create-space-var
                         create-subspace-var
                         bridge-create-space-var
                         bridge-set-child-var])
      (with-redefs-fn
        {bridge-create-space-var
         (fn [client request bridge-timeout on-success _]
           (swap! calls conj [:create-space client request bridge-timeout])
           (on-success (if (= "Project Docs" (::schemas/room-name request))
                         "!subspace:example.org"
                         "!space:example.org"))
           (->StubCloseable (atom 0)))

         bridge-set-child-var
         (fn [client space-id child-room-id child-content bridge-timeout on-success _]
           (swap! calls conj [:set-child client space-id child-room-id child-content bridge-timeout])
           (on-success "$child")
           (->StubCloseable (atom 0)))}
        (fn []
          (is (= "!space:example.org"
                 (realize-task
                  ((var-get create-space-var)
                   :client-handle
                   create-opts))))
          (is (= "!subspace:example.org"
                 (realize-task
                  ((var-get create-subspace-var)
                   :client-handle
                   "!parent:example.org"
                   subspace-opts)))))))
    (is (= [[:create-space :client-handle create-opts nil]
            [:create-space :client-handle {::schemas/room-name "Project Docs"
                                           ::schemas/topic     "Documentation"}
             timeout]
            [:set-child :client-handle "!parent:example.org" "!subspace:example.org"
             {::schemas/via       #{"example.org"}
              ::schemas/order     "a"
              ::schemas/suggested true}
             timeout]]
           @calls))))

(deftest space-relation-and-hierarchy-task-surfaces-test
  (let [hierarchy-var            (resolve-space-var 'hierarchy)
        set-child-var            (resolve-space-var 'set-child)
        remove-child-var         (resolve-space-var 'remove-child)
        set-parent-var           (resolve-space-var 'set-parent)
        remove-parent-var        (resolve-space-var 'remove-parent)
        bridge-hierarchy-var     (resolve-bridge-var 'space-hierarchy)
        bridge-set-child-var     (resolve-bridge-var 'set-space-child)
        bridge-remove-child-var  (resolve-bridge-var 'remove-space-child)
        bridge-set-parent-var    (resolve-bridge-var 'set-space-parent)
        bridge-remove-parent-var (resolve-bridge-var 'remove-space-parent)
        timeout                  (Duration/ofSeconds 5)
        child-content            {::schemas/via          #{"example.org"}
                                  ::schemas/order        "b"
                                  ::schemas/suggested    false
                                  ::schemas/external-url "https://example.org/child"}
        parent-content           {::schemas/via       #{"example.org"}
                                  ::schemas/canonical true}
        calls                    (atom [])]
    (doseq [[var-value message]
            [[hierarchy-var "ol.trixnity.space/hierarchy is missing"]
             [set-child-var "ol.trixnity.space/set-child is missing"]
             [remove-child-var "ol.trixnity.space/remove-child is missing"]
             [set-parent-var "ol.trixnity.space/set-parent is missing"]
             [remove-parent-var "ol.trixnity.space/remove-parent is missing"]
             [bridge-hierarchy-var "ol.trixnity.internal.bridge/space-hierarchy is missing"]
             [bridge-set-child-var "ol.trixnity.internal.bridge/set-space-child is missing"]
             [bridge-remove-child-var "ol.trixnity.internal.bridge/remove-space-child is missing"]
             [bridge-set-parent-var "ol.trixnity.internal.bridge/set-space-parent is missing"]
             [bridge-remove-parent-var "ol.trixnity.internal.bridge/remove-space-parent is missing"]]]
      (is (some? var-value) message))
    (when (every? some? [hierarchy-var
                         set-child-var
                         remove-child-var
                         set-parent-var
                         remove-parent-var
                         bridge-hierarchy-var
                         bridge-set-child-var
                         bridge-remove-child-var
                         bridge-set-parent-var
                         bridge-remove-parent-var])
      (with-redefs-fn
        {bridge-hierarchy-var
         (fn [client space-id request bridge-timeout on-success _]
           (swap! calls conj [:hierarchy client space-id request bridge-timeout])
           (on-success {::schemas/rooms []})
           (->StubCloseable (atom 0)))

         bridge-set-child-var
         (fn [client space-id child-room-id content bridge-timeout on-success _]
           (swap! calls conj [:set-child client space-id child-room-id content bridge-timeout])
           (on-success "$child")
           (->StubCloseable (atom 0)))

         bridge-remove-child-var
         (fn [client space-id child-room-id bridge-timeout on-success _]
           (swap! calls conj [:remove-child client space-id child-room-id bridge-timeout])
           (on-success "$remove-child")
           (->StubCloseable (atom 0)))

         bridge-set-parent-var
         (fn [client room-id parent-space-id content bridge-timeout on-success _]
           (swap! calls conj [:set-parent client room-id parent-space-id content bridge-timeout])
           (on-success "$parent")
           (->StubCloseable (atom 0)))

         bridge-remove-parent-var
         (fn [client room-id parent-space-id bridge-timeout on-success _]
           (swap! calls conj [:remove-parent client room-id parent-space-id bridge-timeout])
           (on-success "$remove-parent")
           (->StubCloseable (atom 0)))}
        (fn []
          (is (= {::schemas/rooms []}
                 (realize-task
                  ((var-get hierarchy-var)
                   :client-handle
                   "!space:example.org"
                   {::schemas/from           "batch"
                    ::schemas/limit          10
                    ::schemas/max-depth      2
                    ::schemas/suggested-only true
                    ::schemas/timeout        timeout}))))
          (is (= "$child"
                 (realize-task
                  ((var-get set-child-var)
                   :client-handle
                   "!space:example.org"
                   "!room:example.org"
                   child-content
                   {::schemas/timeout timeout}))))
          (is (= "$remove-child"
                 (realize-task
                  ((var-get remove-child-var)
                   :client-handle
                   "!space:example.org"
                   "!room:example.org"
                   {::schemas/timeout timeout}))))
          (is (= "$parent"
                 (realize-task
                  ((var-get set-parent-var)
                   :client-handle
                   "!room:example.org"
                   "!space:example.org"
                   parent-content))))
          (is (= "$remove-parent"
                 (realize-task
                  ((var-get remove-parent-var)
                   :client-handle
                   "!room:example.org"
                   "!space:example.org")))))))
    (is (= [[:hierarchy :client-handle "!space:example.org"
             {::schemas/from           "batch"
              ::schemas/limit          10
              ::schemas/max-depth      2
              ::schemas/suggested-only true}
             timeout]
            [:set-child :client-handle "!space:example.org" "!room:example.org"
             child-content timeout]
            [:remove-child :client-handle "!space:example.org" "!room:example.org" timeout]
            [:set-parent :client-handle "!room:example.org" "!space:example.org"
             parent-content nil]
            [:remove-parent :client-handle "!room:example.org" "!space:example.org" nil]]
           @calls))))

(deftest space-local-flow-surfaces-test
  (let [get-all-var            (resolve-space-var 'get-all)
        get-all-flat-var       (resolve-space-var 'get-all-flat)
        get-children-var       (resolve-space-var 'get-children)
        get-child-var          (resolve-space-var 'get-child)
        get-parents-var        (resolve-space-var 'get-parents)
        bridge-spaces-var      (resolve-bridge-var 'spaces)
        bridge-spaces-flat-var (resolve-bridge-var 'spaces-flat)
        bridge-children-var    (resolve-bridge-var 'space-children)
        bridge-child-var       (resolve-bridge-var 'space-child)
        bridge-parents-var     (resolve-bridge-var 'space-parents)
        calls                  (atom [])]
    (doseq [[var-value message]
            [[get-all-var "ol.trixnity.space/get-all is missing"]
             [get-all-flat-var "ol.trixnity.space/get-all-flat is missing"]
             [get-children-var "ol.trixnity.space/get-children is missing"]
             [get-child-var "ol.trixnity.space/get-child is missing"]
             [get-parents-var "ol.trixnity.space/get-parents is missing"]
             [bridge-spaces-var "ol.trixnity.internal.bridge/spaces is missing"]
             [bridge-spaces-flat-var "ol.trixnity.internal.bridge/spaces-flat is missing"]
             [bridge-children-var "ol.trixnity.internal.bridge/space-children is missing"]
             [bridge-child-var "ol.trixnity.internal.bridge/space-child is missing"]
             [bridge-parents-var "ol.trixnity.internal.bridge/space-parents is missing"]]]
      (is (some? var-value) message))
    (when (every? some? [get-all-var
                         get-all-flat-var
                         get-children-var
                         get-child-var
                         get-parents-var
                         bridge-spaces-var
                         bridge-spaces-flat-var
                         bridge-children-var
                         bridge-child-var
                         bridge-parents-var])
      (with-redefs-fn
        {bridge-spaces-var
         (fn [client]
           (swap! calls conj [:spaces client])
           ::spaces-flow)

         bridge-spaces-flat-var
         (fn [client]
           (swap! calls conj [:spaces-flat client])
           ::spaces-flat-flow)

         bridge-children-var
         (fn [client space-id]
           (swap! calls conj [:children client space-id])
           ::children-flow)

         bridge-child-var
         (fn [client space-id child-room-id]
           (swap! calls conj [:child client space-id child-room-id])
           ::child-flow)

         bridge-parents-var
         (fn [client room-id]
           (swap! calls conj [:parents client room-id])
           ::parents-flow)

         #'internal/observe-keyed-flow-map
         (fn [client kotlin-flow]
           [:keyed client kotlin-flow])

         #'internal/observe-flow
         (fn [client kotlin-flow]
           [:flow client kotlin-flow])}
        (fn []
          (is (= [:keyed :client-handle ::spaces-flow]
                 ((var-get get-all-var) :client-handle)))
          (is (= [:flow :client-handle ::spaces-flat-flow]
                 ((var-get get-all-flat-var) :client-handle)))
          (is (= [:keyed :client-handle ::children-flow]
                 ((var-get get-children-var) :client-handle "!space:example.org")))
          (is (= [:flow :client-handle ::child-flow]
                 ((var-get get-child-var) :client-handle "!space:example.org" "!room:example.org")))
          (is (= [:keyed :client-handle ::parents-flow]
                 ((var-get get-parents-var) :client-handle "!room:example.org"))))))
    (is (= [[:spaces :client-handle]
            [:spaces-flat :client-handle]
            [:children :client-handle "!space:example.org"]
            [:child :client-handle "!space:example.org" "!room:example.org"]
            [:parents :client-handle "!room:example.org"]]
           @calls))))
