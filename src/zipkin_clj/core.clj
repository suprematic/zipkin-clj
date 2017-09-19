(ns zipkin-clj.core
  "Before tracing you should set a sender fn passing it to set-sender!.
  The default sender does nothing.

  IMPORTANT:
  Start time is captured in microseconds as System/currentTimeMillis * 1000.
  Duration time is measured in microseconds as a difference between
  System/nanoTime devided by 1000.")

(declare start-span finish-span!)

;; ====================================================================
;; Internal

(def ^:dynamic *current-span* nil)

(defprotocol ISpanStorage
  (get-span [this] "Returns current span or nil if absent.")
  (push-span!
    [this span]
    "Sets span as current. Return value is not specified.")
  (update-span!
    [this f]
    "Makes current span to be (f current-span). If there is no current span
  does nothing.
  All changes made by f must be commutative.")
  (pop-span!
    [this]
    "Makes current span to be as it was before push-span!
  Return value is not specified."))

(defrecord DefaultSpanStorage []
  ISpanStorage
  (get-span [_] (if-let [span *current-span*] @span))
  (push-span! [_ span] (push-thread-bindings {#'*current-span* (atom span)}))
  (update-span!  [_ f] (if-let [span *current-span*] (swap! span f)))
  (pop-span! [_] (pop-thread-bindings)))

 ; TODO possibly it should print something
(defn- default-sender [_])

(def ^:private *sender (atom default-sender))

(def ^:private *storage (atom (DefaultSpanStorage.)))

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

(defn trace!*
  [opts f]
  (push-span! @*storage (start-span opts))
  (try
    (f)
    (finally
      (finish-span! (get-span @*storage ))
      (pop-span! @*storage))))

;; ====================================================================
;; API

(defn set-sender!
  [sender]
  (reset! *sender sender))

(defn set-storage!
  [storage]
  (reset! *storage storage))

(defn tag
  [span tags]
  (update span :tags merge tags))

(defn annotate
  [span & annotations]
  (let [timestamp (current-time-us)
        annotations (map #(annotation timestamp %) annotations)]
    (update span :annotations concat annotations)))

(defn current-span []
  (get-span @*storage ))

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
      (if-let [{:keys [traceId localEndpoint id]} parent]
        {:traceId traceId
         :localEndpoint localEndpoint
         :parentId id}
        {:traceId (id64)})
      (if service
        {:localEndpoint {:serviceName service}}))))

(defn child-span
  [opts]
  (start-span (assoc opts :parent (current-span))))

(defn finish-span!
  [span]
  (let [us (duration-us (::start span))
        span (-> span (assoc :duration us) (dissoc ::start))]
    (@*sender [span])))

(defn tag!
  [tags]
  (update-span! @*storage #(tag % tags)))

(defn annotate!
  [& annotations]
  (update-span! @*storage #(apply annotate % annotations)))

(defmacro child-trace!
  "recur cannot cross trace! boundaries."
  [opts & body]
  `(trace!* (assoc ~opts :parent (current-span)) #(do ~@body)))

(defmacro trace!
  "recur cannot cross trace! boundaries."
  [opts & body]
  `(trace!* ~opts #(do ~@body)))
