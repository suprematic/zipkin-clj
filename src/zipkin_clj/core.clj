(ns zipkin-clj.core
  "Before tracing you should set a sender fn passing it to set-sender!.
  The default sender does nothing.

  IMPORTANT:
  Start time is captured in microseconds as System/currentTimeMillis * 1000.
  Duration time is measured in microseconds as a difference between
  System/nanoTime devided by 1000.")

;; ====================================================================
;; Internal

 ; TODO possibly it should print something
(defn- default-sender [_])

(def ^:private *sender (atom default-sender))

(def ^:dynamic *current-span* nil)

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

(defn- opt-update-span!  [f]
  (if-let [span *current-span*]
    (swap! span f)))

;; ====================================================================
;; API

(defn set-sender!
  [sender]
  (reset! *sender sender))

(defn tag
  [span tags]
  (update span :tags merge tags))

(defn annotate
  [span & annotations]
  (let [timestamp (current-time-us)
        annotations (map #(annotation timestamp %) annotations)]
    (update span :annotations concat annotations)))

(defn tag!
  [tags]
  (opt-update-span! #(tag % tags)))

(defn annotate!
  [& annotations]
  (opt-update-span! #(apply annotate % annotations)))

(defn current-span []
  (if-let [span *current-span*]
    @span))

(defn start-span
  [{span-name :span
    service :service
    parent :parent
    annotations :annotations
    tags :tags
    :as opts}]
  (let [timestamp (current-time-us)]
    (merge
      {::start (start-time)
       :id (id64)
       :name span-name
       :timestamp timestamp
       :annotations (map #(annotation timestamp %) annotations)
       :tags (or tags {})}
      (if-let [{:keys [traceId localEndpoint id]} (if (contains? opts :parent)
                                                    parent
                                                    (current-span))]
        {:traceId traceId
         :localEndpoint localEndpoint
         :parentId id}
        {:traceId (id64)})
      (if service
        {:localEndpoint {:serviceName service}}))))

(defn finish-span
  [span]
  (let [us (duration-us (::start span))
        span (-> span (assoc :duration us) (dissoc ::start))]
    (@*sender [span])))

(defmacro trace!
  [opts & body]
  `(binding [*current-span* (atom (start-span ~opts))]
     (try
       ~@body
       (finally
         (finish-span @*current-span*)))))
