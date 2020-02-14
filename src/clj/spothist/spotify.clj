(ns spothist.spotify
  (:require
   [clojure.core.async :as a]
   [spothist.config :refer [env]]
   [ring.util.codec :refer [form-encode]]
   [spothist.util :refer [http-async success?]])
  (:import java.time.Instant))

(def client-id (:client-id env))
(def client-secret (:client-secret env))
(def redirect-uri (:redirect-uri env))
(def scopes "user-read-recently-played")

(defn get-recently-played
  [token after]
  (http-async {:method :get
               :url "https://api.spotify.com/v1/me/player/recently-played"
               :query-params (merge {:limit 50} (when after {:after after}))
               :as :json
               :oauth-token token}))

;; Login stuff

;; returns nil on failure for any reason
(defn- get-token [m]
  (a/go
    (if-let
     [resp (a/<!
            (http-async {:method :post
                         :url "https://accounts.spotify.com/api/token"
                         :as :json
                         :form-params (merge m {:client_id client-id
                                                :client_secret client-secret})}))]
      (when (success? resp)
        (let [{{:keys [expires_in]} :body} resp]
          (-> (:body resp)
              (select-keys [:access_token :refresh_token])
              (assoc :token_expiry (.plusSeconds (Instant/now) expires_in))))))))

(defn refresh-token [refresh_token]
  (get-token {:refresh_token refresh_token
              :grant_type "refresh_token"}))

(defn get-login-redirect [state]
  (str
   "https://accounts.spotify.com/authorize?"
   (form-encode {:response_type "code"
                 :client_id client-id
                 :scope scopes
                 :redirect_uri redirect-uri
                 :state state})))

(defn backend-login [code]
  (a/go
    (if-let [tokens (a/<! (get-token {:code code
                                      :redirect_uri redirect-uri
                                      :grant_type "authorization_code"}))]
      (if-let [resp (a/<! (http-async {:method :get
                                       :url "https://api.spotify.com/v1/me"
                                       :oauth-token (:access_token tokens)
                                       :as :json}))]
        (when (success? resp)
          (assoc tokens :spotify_id (get-in resp [:body :id])))))))
