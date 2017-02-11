;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.common-test
  (:require [kale.common :as common]
            [clojure.test :as t]
            [slingshot.test :refer :all]))

(common/set-language :en)

(t/deftest set-invalid-language
  (t/is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"Language 'jk' is not available."
         (common/set-language :jk))))

(t/deftest set-existing-language
  (common/set-language :en)
  (t/is (= @common/my-language :en))
  (common/set-language :fr)
  (t/is (= @common/my-language :fr))
  (common/set-language :en))

(t/deftest get-message-en
  (common/set-language :en)
  (t/is (= "Logging in..."
           (common/get-command-msg :login-messages :login-start))))

(t/deftest get-message-fr
  (common/set-language :fr)
  (t/is (= "<français> Logging in..."
           (common/get-command-msg :login-messages :login-start)))
  (common/set-language :en))

(t/deftest missing-msg
  (common/set-language :en)
  (t/is (= "[unknown :en message :help-messages :fake]"
           (common/get-command-msg :help-messages :fake))))

(def cli-options {
                  :opt1 #{"-opt1" "--opt1"}
                  :opt2 #{"-opt2" "--opt2"}})

(t/deftest read-no-flags
  (t/is (= {}
           (common/get-options nil cli-options))))

(t/deftest read-valid-flags
  (t/is (= {:opt1 true
            :opt2 true}
           (common/get-options ["--opt1" "-opt2"] cli-options))))

(t/deftest read-bad-flags
  (t/is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"Unknown option: -badopt"
         (common/get-options ["--opt1" "-badopt"] cli-options))))

(t/deftest reject-extra-args-indeed
  (t/is (thrown+-with-msg?
         [:type :kale.common/fail]
         #"Too many arguments provided to .*action.* Please omit .*junk.*"
         (common/reject-extra-args ["junk"] "action"))))

(t/deftest reject-extra-args-no-args
  (t/is (= nil
         (common/reject-extra-args nil "action"))))

(t/deftest prompt-user-valid-value
  (with-redefs [read-line (fn [] "value")]
    (t/is (= "Input? "
             (with-out-str (t/is (= "value"
                                    (common/prompt-user "Input? " true))))))))

(t/deftest prompt-user-valid-blank
  (with-redefs [read-line (fn [] "")]
    (t/is (= "Input? "
             (with-out-str (t/is (= ""
                                    (common/prompt-user "Input? " true))))))))

(t/deftest prompt-user-invalid
  (with-redefs [read-line (fn [] "")]
    (t/is (thrown+-with-msg?
           [:type :kale.common/fail]
           #"Invalid input"
           (with-out-str
             (common/prompt-user "Input? " false))))))

(t/deftest prompt-user-hidden-valid-value
  (with-redefs [read-line (fn [] "value")]
    (t/is (= "Input? "
             (with-out-str
               (t/is (= "value"
                        (common/prompt-user-hidden "Input? " true))))))))

(t/deftest prompt-user-hidden-valid-blank
  (with-redefs [read-line (fn [] "")]
    (t/is (= "Input? "
             (with-out-str
               (t/is (= ""
                        (common/prompt-user-hidden "Input? " true))))))))

(t/deftest prompt-user-hidden-invalid
  (with-redefs [read-line (fn [] "")]
    (t/is (thrown+-with-msg?
           [:type :kale.common/fail]
           #"Invalid input"
           (with-out-str
             (common/prompt-user-hidden "Input? " false))))))

(t/deftest prompt-user-yn-true
  (with-redefs [common/prompt-user (fn [_ _] "Y")]
    (t/is (common/prompt-user-yn "Input (y/n)? "))))

(t/deftest prompt-user-yn-false
  (with-redefs [common/prompt-user (fn [_ _] "N")]
    (t/is (not (common/prompt-user-yn "Input (y/n)? ")))))

(t/deftest prompt-user-yn-invalid
  (with-redefs [common/prompt-user (fn [_ _] "dunno")]
    (t/is (thrown+-with-msg?
           [:type :kale.common/fail]
           #"Invalid input"
           (with-out-str
             (common/prompt-user-yn "Input (y/n)? "))))))

(t/deftest unknown-action-blank
  (t/is (thrown+-with-msg?
         [:type :kale.common/fail]
         (re-pattern (str "Please specify what you want to action.*"
                          "Available actions for action.*"
                          "   foo.*"
                          "   bar.*"))
         (common/unknown-action "" "action" ["foo" "bar"]))))

(t/deftest unknown-action-bad-action
  (t/is (thrown+-with-msg?
         [:type :kale.common/fail]
         (re-pattern (str "Don't know how to 'action junk'.*"
                          "Available actions for action.*"
                          "   foo.*"
                          "   bar.*"))
         (common/unknown-action "junk" "action" ["foo" "bar"]))))
