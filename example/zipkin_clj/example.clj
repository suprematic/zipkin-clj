(ns zipkin-clj.example
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [zipkin-clj.core :as zipkin]))


;; Creating the sender
(defn- http-trace-sender []
  (let [zipkin-url (str (System/getProperty "zipkin.url") "/api/v2/spans")]
    (fn [spans]
      (let [json-body (json/generate-string spans)]
        (http/post zipkin-url
          {:body json-body
           :content-type :json})))))


;; Configure tracing
(zipkin/configure!
  :endpoint {:service-name "my-service@myhost"}
  :sender (http-trace-sender))


(defn- sleep-more []
  (zipkin/child-trace!
    {:span "sleep-more"
     :tags {:sleep 500}}
    (Thread/sleep 500)))


(defn- sleep []
  (zipkin/child-trace!
    {:span "sleep"
     :tags {:sleep 100}}
    (Thread/sleep 100)))


(defn- go-to-bed []
  (zipkin/annotate! "in-bed"))


(defn- wake-up []
  (zipkin/annotate! "awake"))


(defn main []
  (zipkin/trace!
    {:span "night"}
    (go-to-bed)
    (sleep)
    (sleep-more)
    (wake-up)))
