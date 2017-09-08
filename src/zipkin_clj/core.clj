(ns zipkin-clj.core)

;; ====================================================================
;; Internal

(def ^:private *sender (atom (fn [_])))

(def ^:private ^:dynamic *current-span* nil)

(defn- start-time []
  (System/nanoTime))

(defn- current-time-us []
  (* (System/currentTimeMillis) 1000))

(defn- duration-ns [start-time]
  (- (System/nanoTime) start-time))

(defn- duration-us [start-time]
  (let [us (quot (duration-ns start-time) 1000)]
    (if (= 0 us) 1 us)))

(defn- id64 []
  (format "%016x" (.nextLong (java.util.concurrent.ThreadLocalRandom/current))))

(defn- annotation [timestamp value]
  {:timestamp timestamp
   :value value})

(defn- new-span
  [{span-name :span
    service :service
    parent :parent
    annotations :annotations
    tags :tags}]
  (let [timestamp (current-time-us)]
    (merge
      {:id (id64)
       :name span-name
       :timestamp timestamp
       :annotations
       (map #(annotation timestamp %) annotations)
       :tags (or tags {})}
      (if-let [{:keys [traceId localEndpoint id]} (or parent *current-span*)]
        {:traceId traceId
         :localEndpoint localEndpoint
         :parentId id}
        (merge
          {:traceId (id64)}
          (if service
            {:localEndpoint {:serviceName service}}))))))

;; ====================================================================
;; API

(defn set-sender!
  [sender]
  (reset! *sender sender))

(defn trace!*
  [opts f]
  (let [start (start-time)
        span (new-span opts)]
    (binding [*current-span* span]
      (try
        (f)
        (finally
          (let [span *current-span*
                us (duration-us start)
                span (assoc span :duration us)]
            (@*sender [span])))))))

(defmacro trace!
  [opts & body]
  `(trace!* ~opts #(do ~@body)))

(defn tag!
  [tags]
  (if-let [span *current-span*]
    (set! *current-span* (update-in span [:tags] merge tags))))

(defn annotate!
  [& annotations]
  (if-let [span *current-span*]
    (let [timestamp (current-time-us)
          annotations (map #(annotation timestamp %) annotations)]
      (set! *current-span* (update-in span [:annotations] concat annotations)))))

(defn current-span []
  *current-span*)
