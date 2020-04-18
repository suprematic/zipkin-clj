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
  "Sets the storage used when updating the current span.
  `storage` is an object implementing `zipkin-clj.core/ISpanStorage`.
  Returns `storage`.

  The default storage uses thread-local bindings to store the stack of
  spans."
  [storage]
  (reset! *storage storage))


(defn set-endpoint!
  "Sets the enpoint description which is included into spans sent
  by the process.

  `endpoint` is a map of keyword to any value the sender function
  is able to serialize.

  The following keywords are recognized:

  - `:service-name` - the name fo the service/application. Is sent
    as the \"serviceName\" field of the endpoint description. If
    both `:service-name` and `:serviceName` keywords are present,
    the former overwrites the latter.

  There is no default value. Should be set before creating the first
  span."
  [endpoint]
  (let [endpoint (set/rename-keys endpoint {:service-name :serviceName})]
    (reset! *endpoint endpoint)))


(defn configure!
  "Allows setting all tracing parameters at once.
  `opts` is a map with the following keys:

  - `:sender` - a sender function,
  - `:storage` - a `zipkin-clj.core/ISpanStorage` implementation,
  - `:endpoint` - an endpoint description map

  Returned value is not specified.

  See `set-sender!`, `set-storage!`, `set-endpoint!`."
  [& {:keys [sender storage endpoint] :as opts}]
  (when (contains? opts :sender)
    (set-sender! sender))
  (when (contains? opts :storage)
    (set-storage! storage))
  (set-endpoint! endpoint))


(defn tag
  "Adds `tags` to `span`.
  `tags` is a map of keyword/string to a scalar value.

  Returns the updated span."
  [span tags]
  (update span :tags merge tags))


(defn annotate
  "Adds `annotations` with the current timestamp to `span`.
  `annotations` is a sequence of strings.

  Returns the updated span."
  [span & annotations]
  (let [timestamp (current-time-us)
        annotations (mapv #(annotation timestamp %) annotations)]
    (update span :annotations concat annotations)))


(defn current-span
  "Returns the current span or `nil` if none."
  []
  (get-span @*storage))


(defn span
  "Creates a span.

  The options:

  - `:span` - the span name
  - `:trace-id` - a string representation of the trace id. The parent's
    trace id is used if `:parent` is provided. Generated if absent.
  - `:id` - a string representation of the span id. Generated if absent.
  - `:annotations` - a sequence of annotation strings (the current
    timestamp is used).
  - `:tags` - a map of the span tags (string/keyword -> scalar).
  - `:timestamp` - the span start timestamp in microseconds.
  - `:parent` - the parent span.
  - `:endpoint` - the endpoint description. Overwrite the value set by
    `set-endpoint!`.
  - `:sample?` - the sampling decision. If true, the span is sampled
    (passed to the sender function).
  - `:debug?` - if true, the span is sampled no matter what sampling
    decision is.

  Returns the created span."
  [{span-name :span
    :keys [trace-id sample? debug? endpoint parent annotations tags id timestamp]
    :as _opts}]
  (merge
    {:id (or id (id64))
     :name span-name
     ::sample? (if debug? true (if (some? sample?) sample? true))
     :annotations (mapv #(annotation (current-time-us) %) annotations)
     :tags (or tags {})}
    (when timestamp
      {:timestamp timestamp})
    (when debug?
      {:debug true})
    (if-some [{:keys [traceId id]} parent]
      {:traceId traceId
       :parentId id}
      {:traceId (or trace-id (id64))})
    (when-let [endpoint (or endpoint @*endpoint)]
      {:localEndpoint endpoint})))


(defn start-span
  "Creates a span with the current timestamp.
  Returns the created span.

  See `span` for the list of options."
  [opts]
  (-> opts
    (assoc :timestamp (current-time-us))
    (span)
    (assoc ::start (start-time))))


(defn child-span
  "Creates a span with the current timestamp and the current span
  as the parent.
  Returns the created span.

  See `span` for the list of options."
  [opts]
  (start-span (assoc opts :parent (current-span))))


(defn finish-span
  "Adds duration to the span calculating it as a difference between
  the current time and the start time. Does nothing if span has no
  start time.
  Returns the updated span."
  [span]
  (if-some [start-us (::start span)]
    (assoc span :duration (duration-us start-us))
    (if-some [timestamp (:timestamp span)]
      (assoc span :duration (- (current-time-us) timestamp))
      span)))


(defn send-span!
  "If sampling decision for `span` evaluates to true, passes
  externalized span to the sender.
  Returned value is not specified.

  See `set-sender!`."
  [span]
  (when (or (:debug span) (::sample? span))
    (@*sender [(externalize span)])))


(defn tag!
  "Adds `tags` to the current span. If there is no current span, does
  nothing.
  Returns the updated span or `nil` if none.

  See `tag`."
  [tags]
  (update-span! @*storage #(tag % tags)))


(defn annotate!
  "Adds `annotations` with the current timestamp to the current `span`.
  If there is no current span, does nothing.
  Returns the updated span.

  See `annotate`."
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
