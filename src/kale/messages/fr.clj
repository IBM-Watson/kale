;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.messages.fr)

(def messages
  "French output text, keyed by area."
  {:help-messages
    {:help "Je ne parle pas français"
     :status "French for 'status'"
     :login "<français> Log user in and load credentials"
     :logout "<français> Log user out and clear credentials"}

   :login-messages
    {:login-start "<français> Logging in..."
     :login-done "<français> Log in successful!"
     :logout-start "<français> Logging out..."
     :logout-done "<français> Log out successful!"}})
