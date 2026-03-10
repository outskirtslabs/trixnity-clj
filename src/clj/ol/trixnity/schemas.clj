(ns ol.trixnity.schemas
  (:require
   [com.fulcrologic.guardrails.malli.registry :as gr.reg]
   [malli.error :as me]
   [malli.core :as m]
   [malli.util :as mu]
   [malli.registry :as mr])
  (:import
   (net.folivo.trixnity.client MatrixClient)))

(defn schemas
  [_]
  {::homeserver-url :string
   ::username       :string
   ::password       :string
   ::store-path     :string
   ::media-path     :string
   ::LoginRequest
   [:map
    [::homeserver-url :string]
    [::username :string]
    [::password :string]
    [::store-path :string]
    [::media-path :string]]
   ::FromStoreRequest
   [:map
    [::store-path :string]
    [::media-path :string]]
   ::StartSyncRequest
   [:map
    [::client [:fn #(instance? MatrixClient %)]] ]})

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
