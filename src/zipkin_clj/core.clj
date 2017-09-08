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

(defn- opt-update-span!  [f]
  (if-let [span *current-span*]
    (set! *current-span* (f span))))

;; ====================================================================
;; API

(defn set-sender!
  [sender]
  (reset! *sender sender))

(defn start-span
  [{span-name :span
    service :service
    parent :parent
    annotations :annotations
    tags :tags}]
  (let [timestamp (current-time-us)]
    (merge
      {::start (start-time)
       :id (id64)
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

(defn finish-span
  [span]
  (let [us (duration-us (::start span))
        span (-> span (assoc :duration us) (dissoc ::start))]
    (@*sender [span])))

(defn trace!*
  [opts f]
  (let [span (start-span opts)]
    (binding [*current-span* span]
      (try
        (f)
        (finally
          (let [span *current-span*]
            (finish-span span)))))))

(defmacro trace!
  [opts & body]
  `(trace!* ~opts #(do ~@body)))

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
  *current-span*)
