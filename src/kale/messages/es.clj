;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.messages.es)

(def messages
  "Spanish output text, keyed by area."
  {:help-messages
    {:help "No habla español"
     :status "Spanish for 'status'"
     :login "<español> Log user in and load credentials"
     :logout "<español> Log user out and clear credentials"}

   :login-messages
    {:login-start "<español> Logging in..."
     :login-done "<español> Log in successful!"
     :logout-start "<español> Logging out..."
     :logout-done "<español> Log out successful!"}})
