;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.main-test
  (:require [kale.main :refer [verbs error-exit] :as sut]
            [kale.aliases :refer [commands]]
            [kale.persistence :refer [read-state]]
            [kale.common :refer [new-line fail] :as common]
            [slingshot.slingshot :refer [throw+]]
            [slingshot.test :refer :all]
            [clojure.test :refer [deftest is]]))

(common/set-language :en)

(defn get-help-msg
  [msg-key & vals]
  (apply common/get-command-msg :help-messages msg-key vals))

(deftest read-flags-empty
  (is (= {:args (empty '())
          :flags []
          :options {}}
         (sut/read-flags []))))

(deftest read-flags-values
  (is (= {:args (seq ["action" "subcommand"])
          :flags ["-flag"]
          :options {:trace true}}
         (sut/read-flags ["action" "-flag" "subcommand" "--trace"]))))

(deftest not-yet-implemented
  (is (thrown+-with-msg?
       [:type :kale.common/fail]
       #"'action' is not implemented yet."
       (sut/not-yet-implemented {} ["action"]))))

(deftest check-command-sets
  (is (= (set (keys sut/verbs)) (set (vals commands)))
      (str "Mismatch between the known commands "
           (set (vals commands))
           " and the verbs we have implementations for"
           (set (keys sut/verbs)))))

(deftest cmd-name-normal-name
  (is (= "action" (sut/get-cmd-name :action))))

(deftest cmd-name-sepcial-name
  (is (= "list" (sut/get-cmd-name :list-info))))

(deftest set-trace-flag
  (with-out-str (sut/-main "help" "--trace"))
  (is @common/trace-enabled)
  (common/set-trace false))

(deftest set-language-key
  (let [lang-key (atom :en)]
    (with-redefs [kale.persistence/read-state (fn []
                                                {:user-selections
                                                 {:language "jk"}})
                  common/set-language (fn [new-key]
                                        (reset! lang-key new-key))]
      (with-out-str (sut/-main "help"))
      (is (= @lang-key :jk)))))

(deftest set-help-flag
  (is (= (str (get-help-msg :create) new-line)
         (with-out-str (sut/-main "cr" "--help")))))

(deftest set-help-first-arg
  (is (= (str (get-help-msg :create) new-line)
         (with-out-str (sut/-main "help" "cr")))))

(deftest set-help-second-arg
  (is (= (str (get-help-msg :create) new-line)
         (with-out-str (sut/-main "cr" "help")))))

(deftest main-usage
  (is (= (str (get-help-msg :help) new-line)
         (with-out-str (sut/-main)))))

(deftest main-require-login
  (with-redefs [commands (fn [_] :nothing)
                read-state (fn [] {})
                error-exit (fn [])]
    (is (= (str "Please login to use the 'nothing' command." new-line)
           (with-out-str (sut/-main "action"))))))

(deftest main-prints-fail-message
  (with-redefs [commands (fn [_] :nothing)
                read-state (fn [] {:services {}})
                verbs {:nothing (fn [_ _ _] (fail "something broke"))}
                error-exit (fn [])]
    (is (= (str "something broke" new-line)
           (with-out-str (sut/-main "action"))))))

(deftest main-prints-http-error-message
  (with-redefs [commands (fn [_] :nothing)
                read-state (fn [] {:services {}})
                verbs {:nothing
                       (fn [_ _ _]
                         (throw+
                          {:status 401
                           :body "something broke"
                           :opts {:method :head
                                  :url "https://api.endpoint.net/v1/info"}}))}
                error-exit (fn [])]
    (is (= (str "A HEAD to: https://api.endpoint.net/v1/info" new-line
                "returned an unexpected error status code 401" new-line
                "something broke" new-line)
           (with-out-str (sut/-main "action"))))))

(deftest main-prints-http-error-message-bytes
  (let [byte-body (bytes (byte-array (map (comp byte int)
                                          "something broke")))]
    (with-redefs [commands (fn [_] :nothing)
                  read-state (fn [] {:services {}})
                  verbs {:nothing
                         (fn [_ _ _]
                           (throw+
                            {:status 401
                             :body byte-body
                             :opts {:method :head
                                    :url "https://api.endpoint.net/v1/info"}}))}
                  error-exit (fn [])]
      (is (= (str "A HEAD to: https://api.endpoint.net/v1/info" new-line
                  "returned an unexpected error status code 401" new-line
                  "something broke" new-line)
             (with-out-str (sut/-main "action")))))))

(deftest main-prints-exception-message
  (with-redefs [commands (fn [_] :nothing)
                read-state (fn [] {:services {}})
                verbs {:nothing (fn [_ _ _]
                                  (throw (Exception. "something broke")))}
                error-exit (fn [])]
    (is (= (str "Something unexpected failed while trying to "
                "process your command. This exception was thrown:" new-line
                "java.lang.Exception: something broke" new-line)
           (with-out-str (sut/-main "action"))))))
