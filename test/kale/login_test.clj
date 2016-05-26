;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.login-test
  (:require [kale.login :as sut]
            [kale.cloud-foundry :as cf]
            [kale.persistence :refer [write-state]]
            [kale.common :refer [new-line] :as common]
            [kale.cloud-foundry-constants :refer :all]
            [clojure.test :as t]
            [clj-http.fake :refer [with-fake-routes-in-isolation]]
            [cheshire.core :as json]
            [slingshot.test :refer :all]))

(common/set-language :en)

(t/deftest get-username-arg
  (t/is (= (str "Using username 'redshirt'" new-line)
           (with-out-str
             (t/is (= "redshirt"
                      (sut/get-username {} "redshirt")))))))

(t/deftest get-username-prompt-no-default
  (with-redefs [common/prompt-user
                (fn [prompt allow-blank]
                  (t/is (and (= "Username? " prompt)
                             (not allow-blank)))
                  "redshirt")]
    (t/is (= "redshirt" (sut/get-username {} nil)))))

(t/deftest get-username-prompt-default-used
  (with-redefs [common/prompt-user
                (fn [prompt allow-blank]
                  (t/is (and (= "Username (default: redshirt)? " prompt)
                             allow-blank))
                  "")]
    (t/is (= (str "Using username 'redshirt'" new-line)
             (with-out-str
               (t/is (= "redshirt"
                        (sut/get-username {:login {:username "redshirt"}}
                                          nil))))))))

(t/deftest get-username-prompt-default-not-used
  (with-redefs [common/prompt-user
                (fn [prompt allow-blank]
                  (t/is (and (= "Username (default: blueshirt)? " prompt)
                             allow-blank))
                  "redshirt")]
    (t/is (= "redshirt"
             (sut/get-username {:login {:username "blueshirt"}} nil)))))

(t/deftest get-endpoint-arg
  (t/is (= (str "Using endpoint 'https://api.ep.bluemix.net'" new-line)
           (with-out-str
             (t/is (= "https://api.ep.bluemix.net"
                      (sut/get-endpoint {} "https://api.ep.bluemix.net")))))))

(t/deftest get-endpoint-bad-format
  (t/is (= (str "WARNING: The parameter 'PASSWORD' doesn't appear to "
                "be an endpoint." new-line
                "         Arguments to login are in the form "
                "'kale login <username> <endpoint>'" new-line
                "Using endpoint 'PASSWORD'" new-line)
           (with-out-str
             (t/is (= "PASSWORD"
                      (sut/get-endpoint {} "PASSWORD")))))))

(t/deftest get-endpoint-prompt-no-default
  (with-redefs [common/prompt-user
                (fn [prompt _]
                  (t/is (= "Endpoint (default: https://api.ng.bluemix.net)? "
                           prompt))
                  "https://api.ep.bluemix.net")]
    (t/is (= "https://api.ep.bluemix.net" (sut/get-endpoint {} nil)))))

(t/deftest get-endpoint-default-used
  (with-redefs [common/prompt-user
                (fn [prompt _]
                  (t/is (= "Endpoint (default: https://api.ep.bluemix.net)? "
                           prompt))
                  "")]
    (t/is (= (str "Using endpoint 'https://api.ep.bluemix.net'" new-line)
             (with-out-str
               (t/is (= "https://api.ep.bluemix.net"
                        (sut/get-endpoint
                         {:login {:endpoint "https://api.ep.bluemix.net"}}
                         nil))))))))

(t/deftest get-endpoint-default-not-used
  (with-redefs [common/prompt-user
                (fn [prompt _]
                  (t/is (= "Endpoint (default: https://api.badpoint.net)? "
                           prompt))
                  "https://api.ep.bluemix.net")]
    (t/is (= "https://api.ep.bluemix.net"
             (sut/get-endpoint
              {:login {:endpoint "https://api.badpoint.net"}} nil)))))

(t/deftest get-password-env
  (with-redefs [sut/get-env (fn [_] "scotty")]
    (t/is (= (str "Using password from environment variable 'KALE_PASSWORD'"
                  new-line)
             (with-out-str
               (t/is (= "scotty" (sut/get-password))))))))

(t/deftest get-password-prompt
  (with-redefs [sut/get-env (fn [_] nil)
                common/prompt-user-hidden (fn [_ _] "scotty")]
    (t/is (= "scotty" (sut/get-password)))))

(t/deftest get-existing-org
  (with-fake-routes-in-isolation
    {(cf-url "/v2/organizations")
     (respond {:body (json/encode orgs-response)})}
    (t/is (= (org-entity "ORG_GUID1" "org1")
             (sut/attempt-to-get-org cf-auth "org1" "redshirt")))))

(t/deftest get-local-org
  (with-fake-routes-in-isolation
    {(cf-url "/v2/organizations")
     (respond {:body (json/encode orgs-response)})}
    (t/is (= (str "Using org 'org2'" new-line)
             (with-out-str
               (t/is (= (org-entity "ORG_GUID2" "org2")
                        (sut/attempt-to-get-org cf-auth nil "org2"))))))))

(t/deftest attempt-nil-org
  (with-fake-routes-in-isolation
    {(cf-url "/v2/organizations")
     (respond {:body (json/encode orgs-response)})}
    (t/is (= (str "Using org 'org1'" new-line)
             (with-out-str
               (t/is (= (org-entity "ORG_GUID1" "org1")
                        (sut/attempt-to-get-org cf-auth nil "redshirt"))))))))

(t/deftest attempt-bad-org
  (with-fake-routes-in-isolation
    {(cf-url "/v2/organizations")
     (respond {:body (json/encode orgs-response)})}
    (t/is (= (str "Unable to find org 'bad-org', using org 'org1' instead"
                  new-line)
             (with-out-str
               (t/is (= (org-entity "ORG_GUID1" "org1")
                        (sut/attempt-to-get-org cf-auth
                                                "bad-org"
                                                "redshirt"))))))))

(t/deftest get-existing-space
  (with-fake-routes-in-isolation
    {(cf-url "/v2/organizations/ORG_GUID/spaces")
     (respond {:body (json/encode spaces-response)})}
    (t/is (= (space-entity "SPACE_GUID1" "space1")
             (sut/attempt-to-get-space cf-auth "ORG_GUID" "space1")))))

(t/deftest attempt-nil-space
  (with-fake-routes-in-isolation
    {(cf-url "/v2/organizations/ORG_GUID/spaces")
     (respond {:body (json/encode spaces-response)})}
    (t/is (= (str "Using space 'space1'" new-line)
             (with-out-str
               (t/is (= (space-entity "SPACE_GUID1" "space1")
                        (sut/attempt-to-get-space cf-auth "ORG_GUID" nil))))))))

(t/deftest attempt-bad-space
  (with-fake-routes-in-isolation
    {(cf-url "/v2/organizations/ORG_GUID/spaces")
     (respond {:body (json/encode spaces-response)})}
    (t/is (= (str
              "Unable to find space 'bad-space', using space 'space1' instead"
              new-line)
             (with-out-str
               (t/is (= (space-entity "SPACE_GUID1" "space1")
                        (sut/attempt-to-get-space
                         cf-auth "ORG_GUID" "bad-space"))))))))

(t/deftest get-org-space
  (with-redefs [sut/attempt-to-get-org
                (fn [_ _ _] (org-entity "ORG_GUID1" "org-name"))
                sut/attempt-to-get-space
                (fn [_ _ _] (space-entity "SPACE_GUID1" "space-name"))]
    (t/is (= {:org "org-name"
              :space "space-name"
              :guid {
                     :org "ORG_GUID1"
                     :space "SPACE_GUID1"}}
             (sut/get-org-space cf-auth "org-name" "space-name" "redshirt")))))

(t/deftest load-user-information
  (let [endpoint (cf-auth :url)
        token (cf-auth :token)
        user "redshirt"]
    (with-redefs [cf/get-services (fn [_ _] (merge entry1 entry2))
                  sut/get-org-space (fn [_ _ _ _] org-space-entry)
                  cf/get-oauth-tokens (fn [_ _ _] {:access_token token})]
      (t/is (= (str "Loading services..." new-line)
               (with-out-str
                 (t/is (= {:login {:username user
                                   :cf-token token
                                   :endpoint endpoint}
                           :services (merge entry1 entry2)
                           :org-space org-space-entry}
                          (sut/load-user-info
                            user "scotty" endpoint
                            {:org-space {:org "org-name"
                                         :space-name "space-name"}})))))))))

(def user-info
  {:login {:username "redshirt"
           :cf-token "TOKEN"
           :endpoint "https://api.endpoint.net"}
   :org-space {:org "org-name"
               :space "space-name"}})

(t/deftest login
  (let [captured-state (atom {})]
    (with-redefs [sut/get-username (fn [_ _] "redshirt")
                  sut/get-endpoint (fn [_ _] "https://api.endpoint.net")
                  sut/get-password (fn [] "scotty")
                  sut/load-user-info (fn [_ _ _ _] user-info)
                  write-state (fn [state] (reset! captured-state state))]
      (t/is (= (str "Logging in..." new-line)
               (with-out-str
                 (t/is (=
                     (str new-line
                          "Log in successful!" new-line new-line
                          "Current environment:" new-line
                          "   user:                         redshirt" new-line
                          "   endpoint:                     "
                          "https://api.endpoint.net" new-line
                          "   org:                          org-name" new-line
                          "   space:                        space-name"
                          new-line)
                     (sut/login {:foo "bar"} (seq '("login")) []))))))
      (t/is (= @captured-state
               {:foo "bar"
                :login {:username "redshirt"
                        :cf-token "TOKEN"
                        :endpoint "https://api.endpoint.net"}
                :org-space {:org "org-name"
                            :space "space-name"}})))))

(t/deftest logout
  (with-redefs [write-state (fn [_])]
    (t/is (= (str "Logging out..." new-line)
             (with-out-str
               (t/is (= (str new-line "Log out successful!" new-line)
                        (sut/logout {} [] []))))))))
