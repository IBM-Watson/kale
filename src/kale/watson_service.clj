;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.watson-service
  (:require [kale.common :refer [trace-enabled get-command-msg]]
            [kale.version :refer [kale-version]]
            [clojure.pprint :refer [pprint]]
            [cheshire.core :as json]
            [clj-http.client :as client]
            [slingshot.slingshot :refer [throw+ try+]]
            [clojure.string :as str]))

(def props #{"java.vendor"
             "java.version"
             "os.arch"
             "os.name"
             "os.version"})

(def user-agent-string
  (str "ibm-watson-kale/" (kale-version)
       " ("
       (str/join "; " (map #(.toString %)
                           (filter (fn [[k v]] (props k))
                                   (System/getProperties))))
       ")"))

(def user-agent-header {:user-agent user-agent-string})

(defn get-msg
  "Return the corresponding service message"
   [msg-key & args]
   (apply get-command-msg :service-messages msg-key args))

(defn setup-multipart
  "Expands a simple hashmap of name:value pairs into
  the richer nested structure needed by clj-http.client/post."
  [params]
  {:multipart (map (fn [[n v]] {:name (name n) :content v})
                   params)})

(defn generate-auth
  "Create authentication parameter to provide to service."
  [username password token]
  (if token
    {:oauth-token token}
    {:basic-auth [username password]}))

(defn trace-api
  [request response]
  (println (get-msg :trace-request))
  (pprint request)
  (println (get-msg :trace-response))
  (if (some? (response :body))
    (let [content-type ((response :headers) "Content-Type")]
      (cond
        (some? (re-find #"zip" content-type))
          (pprint (assoc response :body (get-msg :trace-zip-content)))
        (some? (re-find #"json" content-type))
          (pprint (update-in response [:body] json/decode true))
        :else (pprint response)))
    (pprint response)))

(defn raw
  "Raw HTTP request."
  ([method endpoint] (raw method endpoint "" {}))
  ([method endpoint path] (raw method endpoint path {}))
  ([method {:keys [url username password token]} path options]
   (try+ (let [url (str url path)
               request (merge {:method method
                               :headers user-agent-header
                               :url url} options)
               authorization (generate-auth username password token)
               response (client/request (merge request authorization))]

           (when @trace-enabled (trace-api request response))
           response)
         ;; Only intercept unexpected exceptions here
         (catch (not (number? (:status %))) e
           ;; Make this exception look like an exception thrown by
           ;; clj-http.client, so our caller can give a similar style
           ;; message to the end user.
           (throw+ {:body (str e)
                    :status -1
                    :trace-redirects [url]})))))

(defn text
  "HTTP request, returning just the body of the response as a string."
  ([method endpoint] (text method endpoint "" {}))
  ([method endpoint path] (text method endpoint path {}))
  ([method endpoint path options]
   (let [response (raw method endpoint path options)]
     (:body response))))

(defn json
  "HTTP request, with the response body interpreted as JSON.
   The "
  ([method endpoint] (json method endpoint "" {}))
  ([method endpoint path] (json method endpoint path {}))
  ([method endpoint path options]
   (let [response (raw method endpoint path (merge {:accept :json} options))]
     (with-meta (json/decode (:body response) true) response))))
