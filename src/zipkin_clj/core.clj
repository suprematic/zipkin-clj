(ns zipkin-clj.core
  "Before tracing you should set a sender fn passing it to set-sender!.
  The default sender does nothing.

  IMPORTANT:
  Start time is captured in microseconds as System/currentTimeMillis * 1000.
  Duration time is measured in microseconds as a difference between
  System/nanoTime devided by 1000."

  (:import [java.util.concurrent ThreadLocalRandom])
  (:require [clojure.set :as set]))


(declare start-span finish-span send-span! externalize)


;; ====================================================================
;; Internal
;; ====================================================================


(def ^:dynamic *current-span* nil)


(defprotocol ISpanStorage

  (get-span [this]
    "Returns the current span or nil if absent.")

  (push-span!
    [this span]
    "Sets span as the current. Returns the updated span.")

  (update-span!
    [this f]
    "Sets the current span to (f current-span). If there is no current span
  does nothing. Returns the updated value.
  All changes made by f must be commutative.")

  (pop-span!
    [this]
    "Sets the current span to the previous value (as it was before push-span!)
  Returns the current span."))


(defrecord DefaultSpanStorage []
  ISpanStorage

  (get-span [_]
    (when-some [span *current-span*]
      @span))

  (push-span! [_ span]
    (push-thread-bindings {#'*current-span* (atom span)})
    span)

  (update-span!  [_ f]
    (when-some [span *current-span*]
      (swap! span f)))

  (pop-span! [_]
    (when-let [span *current-span*]
      (pop-thread-bindings)
      @span)))


;; TODO possibly it should print something
(defn- default-sender [_])


(def ^:private *sender (atom default-sender))


(def ^:private *storage (atom (DefaultSpanStorage.)))


(def ^:private *endpoint (atom nil))


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
  (->> (ThreadLocalRandom/current) (.nextLong) (format "%016x")))


(defn- annotation [timestamp value]
  {:timestamp timestamp
   :value value})


(defn trace!*
  [opts f]
  (push-span! @*storage (start-span opts))
  (try
    (f)
    (finally
      (-> @*storage
        pop-span!
        finish-span
        send-span!))))


(defn trace-context!*
  [span f]
  (push-span! @*storage span)
  (try
    (f)
    (finally
      (-> @*storage
        pop-span!
        send-span!))))


;; ====================================================================
;; API
;; ====================================================================


(defn set-sender!
  "Sets the function to use for sampling.
  `sender` is a function taking a list of spans as an argument.
  Returns `sender`.

  The default sender does nothing."
  [sender]
  (reset! *sender sender))


(defn set-storage!
  [storage]
  (reset! *storage storage))


(defn set-endpoint!
  [endpoint]
  (let [endpoint (set/rename-keys endpoint {:service-name :serviceName})]
    (reset! *endpoint endpoint)))


(defn configure!
  [& {:keys [sender storage endpoint] :as opts}]
  (when (contains? opts :sender)
    (set-sender! sender))
  (when (contains? opts :storage)
    (set-storage! storage))
  (set-endpoint! endpoint))


(defn tag
  [span tags]
  (update span :tags merge tags))


(defn annotate
  [span & annotations]
  (let [timestamp (current-time-us)
        annotations (mapv #(annotation timestamp %) annotations)]
    (update span :annotations concat annotations)))


(defn current-span
  []
  (get-span @*storage))


(defn span
  [{span-name :span
    :keys [trace-id sample? debug? service parent annotations tags id timestamp]
    :as _opts}]
  (merge
    {:id (or id (id64))
     :name span-name
     ::sample? (if debug? true (if (some? sample?) sample? true))
     :annotations (mapv #(annotation timestamp %) annotations)
     :tags (or tags {})}
    (when timestamp
      {:timestamp timestamp})
    (when debug?
      {:debug true})
    (if-some [{:keys [traceId id]} parent]
      {:traceId traceId
       :parentId id}
      {:traceId (or trace-id (id64))})
    (when-let [endpoint (if service
                          {:serviceName service}
                          @*endpoint)]
      {:localEndpoint endpoint})))


(defn start-span
  [opts]
  (-> opts
    (assoc :timestamp (current-time-us))
    (span)
    (assoc ::start (start-time))))


(defn child-span
  [opts]
  (start-span (assoc opts :parent (current-span))))


(defn finish-span
  [span]
  (if-some [start-us (::start span)]
    (assoc span :duration (duration-us start-us))
    (if-some [timestamp (:timestamp span)]
      (assoc span :duration (- (current-time-us) timestamp))
      span)))


(defn send-span!
  [span]
  (when (or (:debug span) (::sample? span))
    (@*sender [(externalize span)])))


(defn tag!
  [tags]
  (update-span! @*storage #(tag % tags)))


(defn annotate!
  [& annotations]
  (update-span! @*storage #(apply annotate % annotations)))


(defmacro child-trace!
  "recur cannot cross child-trace! boundaries."
  [opts & body]
  `(trace!* (assoc ~opts :parent (current-span)) #(do ~@body)))


(defmacro trace!
  "recur cannot cross trace! boundaries."
  [opts & body]
  `(trace!* ~opts #(do ~@body)))


(defmacro trace-context!
  "recur cannot cross trace-context! boundaries."
  [span & body]
  `(trace-context!* ~span #(do ~@body)))


(defn externalize
  "Prepares `span` for serialization before sending to Zipkin."
  [span]
  (dissoc span ::start ::sample?))
