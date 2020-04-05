(ns zipkin-clj.core-test
  (:require [clojure.core.match :refer [match]]
            [clojure.test :refer [deftest is]]
            [zipkin-clj.core :as zipkin]))

;;===
#_(ns zipkin-clj.core-test
  (:require [org.httpkit.client :as http]
            [cheshire.core :as json]
            [zipkin-clj.core :as zipkin]))

#_(defn http-sender [encoder spans]
    (let [body (json/generate-string spans)]
      @(http/post "http://127.0.0.1:9411/api/v2/spans" {:body body})))
;;===


;; ====================================================================
;; Internal
;; ====================================================================


(defn- collecting-sender [collect-to-atom spans]
  (swap! collect-to-atom concat spans))


(defmacro ^:private find-matches
  [match-spec coll]
  `(filter
     #(match % ~match-spec true :else false)
     ~coll))


(defn- nested-span [i]
  (zipkin/child-trace!
    {:span "my-span-2"
     :annotations ["sleep250"]
     :tags {"nested span" "yes"}}
    (zipkin/tag! {"i" i})
    (zipkin/annotate! i)))


;; ====================================================================
;; Tests
;; ====================================================================


(deftest a-test
  (let [spans (atom [])]
    (zipkin/set-sender! (partial collecting-sender spans))
    (zipkin/trace!
      {:span "my-span-1"
       :service "my-service"
       :tags {"root span" "yes"}}
      (nested-span 1)
      (nested-span 2))
    (let [spans @spans]
      (is (= 3 (count spans)) "3 spans must be collected")
      (is (apply = (map :traceId spans))
        "all spans must belong to the same trace (have the same trace id)")
      (is (= 3 (count (find-matches
                        {:id (_ :guard #(re-matches #"[a-z0-9]{16}" %))
                         :traceId (_ :guard #(re-matches #"[a-z0-9]{16,32}" %))
                         :localEndpoint {:serviceName "my-service"}
                         :duration (_ :guard pos?)
                         :timestamp (_ :guard integer?)}
                        spans)))
        (str "all spans must have: id, trace id, duration, serice name"
          " and timestamp"))
      (is (= 1 (count (find-matches
                        {:name "my-span-1"
                         :tags {"root span" "yes"}}
                        spans)))
        "root span must present with its name and tags")
      (let [[{root-span-id :id}] (find-matches {:name "my-span-1"} spans)]
        (is (= 1 (count (find-matches
                          {:name "my-span-2"
                           :parentId root-span-id
                           :annotations ([{:value "sleep250"
                                           :timestamp _}
                                          {:value 1
                                           :timestamp _}] :seq)
                           :tags {"nested span" "yes"
                                  "i" 1}}
                          spans)))
          (str "first nested span must present with its name, tags,"
            " parent id and anotations"))
        (is (= 1 (count (find-matches
                          {:name "my-span-2"
                           :parentId root-span-id
                           :annotations ([{:value "sleep250"
                                           :timestamp _}
                                          {:value 2
                                           :timestamp _}] :seq)
                           :tags {"nested span" "yes"
                                  "i" 2}}
                          spans)))
          (str "second nested span must present with its name, tags,"
            " parent id and anotations"))))))
