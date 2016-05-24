;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.search-test
  (:require [kale.search :as sut]
            [clojure.test :refer [deftest is]]
            [slingshot.test :refer :all]
            [kale.common :refer [new-line set-language]]
            [kale.retrieve-and-rank :as rnr]
            [clojure.string :as str]))

(set-language :en)

(deftest search-no-collection
  (with-redefs [rnr/list-clusters (fn [_] [])
                rnr/list-collections (fn [_ _] [])]
    (is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"Please create or select a collection to work with"
         (sut/search {} ["search" "nope"] [])))))

(defn search-success
  [command-line-args]
  (with-redefs [rnr/query (fn [endpoint cluster-id collection-name query-string]
                            (is (= {:url "nowhere"}
                                   endpoint))
                            (is (= "CLUSTER-ID"
                                   cluster-id))
                            (is (= "new-collection"
                                   collection-name))
                            (is (= (if (< 1 (count command-line-args))
                                     (str/join "+" (rest command-line-args))
                                     "*:*")
                                   query-string))
                            {:responseHeader ""
                             :response {:numFound "7"
                                        :docs [{:id "1"
                                                :title "one"}
                                               {:id "2"
                                                :title "two"}]}
                             :highlighting {:1 {:body ["first result"]}
                                            :2 {:body ["second result"]}}})]
    (is (= (str "Found 7 results."
                new-line
                new-line "     id: 1"
                new-line "  title: one"
                new-line "snippet: first result"
                new-line
                new-line "     id: 2"
                new-line "  title: two"
                new-line "snippet: second result"
                new-line)
           (sut/search {:services {:rnr-service
                                   {:credentials
                                    {:url "nowhere"}}}
                        :user-selections
                        {:collection
                         {:service-key "rnr-service"
                          :cluster-id "CLUSTER-ID"
                          :cluster-name "cluster"
                          :collection-name "new-collection"}}}
                       command-line-args
                       [])))))

(deftest search-default
  (search-success ["search"]))

(deftest search-with-terms
  (search-success ["search" "honda" "civic"]))
