;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.cloud-foundry-test
  (:require [kale.cloud-foundry :as cf]
            [kale.cloud-foundry-constants :refer :all]
            [kale.common :refer [set-language prompt-user-hidden]]
            [clj-http.fake :refer [with-fake-routes-in-isolation]]
            [cheshire.core :as json]
            [clojure.test :as t :refer [deftest is]]
            [slingshot.slingshot :refer [try+ throw+]]
            [slingshot.test :refer :all]))

(set-language :en)

(deftest cf-request-invalid-token
  (is (thrown+-with-msg?
      [:type :kale.common/fail]
      (re-pattern (str "The authentication token for this session "
                       "is either invalid or expired.*"
                       "Please run 'kale login' to acquire a new one."))
      (cf/cf-request
        (fn [] (throw+ {:status 401
                        :body (json/encode
                                {"code" 1000
                                 "error_code" "CF-InvalidAuthToken"})}))
        nil))))

(deftest cf-request-other-cf-error
  (let [body (json/encode {"code" 1000 "error_code" "CF-Error"})]
    (is (= body
          (try+
            (cf/cf-request (fn [] (throw+ {:status 401
                                           :body body}))
                           nil)
            (catch (number? (:status %)) e (e :body)))))))

(deftest cf-request-other-exception
  (is (= "Divide by zero"
         (try+
           (cf/cf-request (fn [] (/ 1 0)) nil)
           (catch Exception e (.getMessage e))))))

(deftest cf-request-multiple-pages
  (with-fake-routes-in-isolation
    {(cf-url "/v2/info")
     (respond {:body (json/encode
                       (assoc (results-response ["data1" "data2"])
                              :next_url
                              "/v2/info?page=2"))})
     (cf-url "/v2/info?page=2")
     (respond {:body (json/encode (results-response ["data3"]))})}
    (is (= ["data1" "data2" "data3"]
           (cf/cf-paged-json :get cf-auth "/v2/info")))))

(deftest get-oauth-tokens
  (with-fake-routes-in-isolation
    {(cf-url "/v2/info")
     (respond {:body (json/encode
                       {:authorization_endpoint
                          "https://login.ng.bluemix.net/UAALoginServerWAR"})})
     "https://login.ng.bluemix.net/UAALoginServerWAR/oauth/token"
     (respond {:body (json/encode {:access_token "ACCESS_TOKEN"
                                   :refresh_token "REFRESH_TOKEN"})})}
    (is (= {:access_token "ACCESS_TOKEN"
            :refresh_token "REFRESH_TOKEN"}
           (cf/get-oauth-tokens "redshirt" "scotty" (cf-auth :url))))))

(deftest get-oauth-tokens-sso
  (with-fake-routes-in-isolation
    {(cf-url "/v2/info")
     (respond {:body (json/encode
                       {:authorization_endpoint
                          "https://login.ng.bluemix.net/UAALoginServerWAR"})})
     "https://login.ng.bluemix.net/UAALoginServerWAR/login"
     (respond {:body (json/encode
                       {:prompts {:passcode
                         ["password"
                          "One Time Code (Get one at URL)"]}})})
     "https://login.ng.bluemix.net/UAALoginServerWAR/oauth/token"
     (respond {:body (json/encode {:access_token "ACCESS_TOKEN"
                                   :refresh_token "REFRESH_TOKEN"})})}
    (with-redefs [prompt-user-hidden (fn [prompt _]
                                         (is (= (str "One Time Code "
                                                     "(Get one at URL)? ")
                                                prompt)
                                         "CODE"))]
      (is (= {:access_token "ACCESS_TOKEN"
              :refresh_token "REFRESH_TOKEN"}
             (cf/get-oauth-tokens-sso (cf-auth :url)))))))

(deftest get-user-data-bad-token
  (is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"Unable to determine user ID."
         (cf/get-user-data nil))))

(def get-user-data-indeed
  (is (= "3-3-2222"
         ((cf/get-user-data
         "WlXnkhdzWge0.eyJ1c2VyX2lkIjoiMy0zLTIyMjIifQo=.WlXnkhdzWge0")
         "user_id"))))

(deftest create-space
  (with-fake-routes-in-isolation
    {(cf-url "/v2/spaces?async=true")
     (respond {:body (json/encode (space-entity "SPACE_GUID" "space"))})}
    (is (= (space-entity "SPACE_GUID" "space")
           (cf/create-space cf-auth "ORG_GUID" "USER_GUID" "space")))))

(deftest create-service-entry
  (let [entity (service-entity "RNR_GUID" "rnr-service" "retrieve_and_rank")]
      (is (= entry1 (cf/service-entry entity
                                      (service-keys-response :resources))))))

(deftest create-service-entry-with-no-credentials
  (let [entity (service-entity "SERVICE_GUID3" "service-name3" "service-type3")]
      (is (= {:service-name3 {
                 :key-guid nil
                 :credentials nil
                :plan "standard"
                 :guid "SERVICE_GUID3"
                 :type "service-type3"}}
               (cf/service-entry entity (service-keys-response :resources))))))

(deftest get-service-information
  (with-fake-routes-in-isolation
    {(cf-url "/v2/spaces/SPACE_GUID/summary")
     (respond {:body (json/encode space-summary-response)})
     (cf-url "/v2/service_keys")
     (respond {:body (json/encode service-keys-response)})}
    (with-redefs [cf/service-entry (fn [{:keys [guid]} _]
                    (cond
                      (= guid "RNR_GUID") entry1
                      (= guid "DC_GUID") entry2
                      :else nil))]
      (is (= (merge entry1 entry2) (cf/get-services cf-auth "SPACE_GUID"))))))

(deftest get-service-plan-guid
  (with-fake-routes-in-isolation
    {(cf-url "/v2/spaces/SPACE_GUID/services?q=label%3Aservice-type")
     (respond {:body (json/encode service-type-response)})
     (cf-url "/v2/service_plans?q=service_guid%3ATYPE_GUID")
     (respond {:body (json/encode service-plan-response)})}
    (is (= "PLAN_GUID"
           (cf/get-service-plan-guid
              cf-auth "SPACE_GUID" "service-type" "standard")))))

(deftest get-service-status
  (with-fake-routes-in-isolation
    {(cf-url "/v2/service_instances/SERVICE_GUID")
     (respond {:body (json/encode
                       (service-instance-entity "GUID" "service-name"))})}
    (is (= "create succeeded"
           (cf/get-service-status cf-auth "SERVICE_GUID")))))

(deftest delete-service-key
  (with-fake-routes-in-isolation
    {(cf-url "/v2/service_keys/KEY_GUID?async=true") (respond nil)}
    (is (empty? (cf/delete-service-key cf-auth "KEY_GUID")))))

(deftest delete-service-service
  (with-fake-routes-in-isolation
    {(cf-url "/v2/service_instances/GUID?accepts_incomplete=true&async=true")
     (respond nil)}
    (is (empty? (cf/delete-service cf-auth "GUID")))))
