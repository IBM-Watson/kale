;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.watson-service-test
  (:require [kale.watson-service :as sut]
            [kale.common :refer [new-line]]
            [clj-http.fake :refer [with-fake-routes-in-isolation]]
            [slingshot.test :refer :all]
            [clojure.test :refer [deftest is]]))

(deftest multipart-fun
  (is (= {:multipart [{:name "key1" :content "value1"}
                      {:name "key2" :content "value2"}]}
         (sut/setup-multipart {:key1 "value1"
                               :key2 "value2"}))))

(deftest raw-http-error-status
  (with-fake-routes-in-isolation
    {"http://example.com/raw-http-error-status"
     (fn [_] {:status 400 :body "No good"})}
    (is (thrown+?
         [:status 400]
         (sut/raw :get {:url "http://example.com/raw-http-error-status"})))))

(deftest text-timeout
  (with-fake-routes-in-isolation
    {"http://example.com/text-timeout"
     (fn [_] (throw (java.net.ConnectException. "Operation timed out")))}
    (is (thrown+?
         [:status -1]
         (sut/text :get {:url "http://example.com/text-timeout"})))))

(deftest text-success
  (with-fake-routes-in-isolation
    {"http://example.com/text-success/with/path"
     (fn [_] {:status 200 :body "Body text."})}
    (is (= "Body text."
           (sut/text :get
                     {:url "http://example.com/text-success"}
                     "/with/path")))))

(deftest json-success
  (with-fake-routes-in-isolation
    {"http://example.com/json-success"
     (fn [_] {:status 200 :body "{\"key\" : \"value\"}"})}
    (is (= {:key "value"}
           (sut/json :get
                     {:url "http://example.com/json-success"})))))

(deftest trace-api-json-body
  (is (= (str "REQUEST:" new-line
              "{:method :get,"
              " :url \"https://example.com/api\"}" new-line
              "RESPONSE:" new-line
              "{:status 200," new-line
              " :headers {\"Content-Type\" \"application/json\"}," new-line
              " :body {:key \"value\"}}" new-line)
         (with-out-str
           (sut/trace-api {:method :get
                             :url "https://example.com/api"}
                          {:status 200
                           :headers {"Content-Type" "application/json"}
                           :body "{\"key\" : \"value\"}"})))))

(deftest trace-api-text-body
  (is (= (str "REQUEST:" new-line
              "{:method :get,"
              " :url \"https://example.com/api\"}" new-line
              "RESPONSE:" new-line
              "{:status 200," new-line
              " :headers {\"Content-Type\" \"application/text\"}," new-line
              " :body \"Body text.\"}" new-line)
         (with-out-str
           (sut/trace-api {:method :get
                             :url "https://example.com/api"}
                          {:status 200
                           :headers {"Content-Type" "application/text"}
                           :body "Body text."})))))

(deftest trace-api-zip-body
  (is (= (str "REQUEST:" new-line
              "{:method :get,"
              " :url \"https://example.com/api\"}" new-line
              "RESPONSE:" new-line
              "{:status 200," new-line
              " :headers {\"Content-Type\" \"application/zip\"}," new-line
              " :body \"[ZIP CONTENT]\"}" new-line)
         (with-out-str
           (sut/trace-api {:method :get
                             :url "https://example.com/api"}
                          {:status 200
                           :headers {"Content-Type" "application/zip"}
                           :body "ZIP_BINARY"})))))

(deftest trace-api-no-body
  (is (= (str "REQUEST:" new-line
              "{:method :get,"
              " :url \"https://example.com/api\"}" new-line
              "RESPONSE:" new-line
              "{:status 200}" new-line)
         (with-out-str
           (sut/trace-api {:method :get
                             :url "https://example.com/api"}
                          {:status 200})))))
