(ns ol.trixnity-poc.synapse-register
  (:require
   [babashka.http-client :as http]
   [clojure.string :as str])
  (:import
   (java.nio.charset StandardCharsets)
   (javax.crypto Mac)
   (javax.crypto.spec SecretKeySpec)))

(defn- hmac-sha1-hex [^String secret ^String payload]
  (let [mac (Mac/getInstance "HmacSHA1")]
    (.init mac (SecretKeySpec. (.getBytes secret StandardCharsets/UTF_8)
                               "HmacSHA1"))
    (->> (.doFinal mac (.getBytes payload StandardCharsets/UTF_8))
         (map #(format "%02x" (bit-and % 0xff)))
         (apply str))))

(defn generate-mac
  [{:keys [nonce username password admin? shared-secret user-type]}]
  (let [role    (if admin? "admin" "notadmin")
        payload (str nonce "\u0000"
                     username "\u0000"
                     password "\u0000"
                     role
                     (when user-type
                       (str "\u0000" user-type)))]
    (hmac-sha1-hex shared-secret payload)))

(defn- parse-nonce [body]
  (some->> (re-find #"\"nonce\"\s*:\s*\"([^\"]+)\"" body)
           second))

(defn- escape-json [s]
  (str/escape s {\\       "\\\\"
                 \"       "\\\""
                 \newline "\\n"
                 \return  "\\r"
                 \tab     "\\t"}))

(defn- post-body
  [{:keys [nonce username password display-name admin? mac user-type]}]
  (str "{"
       "\"nonce\":\"" (escape-json nonce) "\""
       ",\"username\":\"" (escape-json username) "\""
       ",\"displayname\":\"" (escape-json display-name) "\""
       ",\"password\":\"" (escape-json password) "\""
       ",\"admin\":" (if admin? "true" "false")
       (when user-type
         (str ",\"user_type\":\"" (escape-json user-type) "\""))
       ",\"mac\":\"" (escape-json mac) "\""
       "}"))

(defn register!
  [{:keys [base-url username password shared-secret admin? display-name user-type]
    :or   {admin? false}}]
  (let [endpoint     (str (str/replace (str base-url) #"/$" "")
                          "/_synapse/admin/v1/register")
        nonce-resp   (http/get endpoint {:throw false})
        nonce        (if (<= 200 (:status nonce-resp) 299)
                       (parse-nonce (:body nonce-resp))
                       nil)
        _            (when-not nonce
                       (throw (ex-info "failed to fetch shared-secret nonce"
                                       {:status (:status nonce-resp)
                                        :body   (:body nonce-resp)})))
        mac          (generate-mac
                      {:nonce         nonce
                       :username      username
                       :password      password
                       :admin?        admin?
                       :shared-secret shared-secret
                       :user-type     user-type})
        body         (post-body
                      {:nonce        nonce
                       :username     username
                       :password     password
                       :display-name (or display-name username)
                       :admin?       admin?
                       :mac          mac
                       :user-type    user-type})
        register-res (http/post endpoint
                                {:throw   false
                                 :headers {:content-type "application/json"}
                                 :body    body})]
    (when-not (<= 200 (:status register-res) 299)
      (throw (ex-info "shared-secret register failed"
                      {:status (:status register-res)
                       :body   (:body register-res)})))
    :registered))
