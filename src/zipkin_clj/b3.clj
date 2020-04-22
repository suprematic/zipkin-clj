(ns zipkin-clj.b3
  "See https://github.com/openzipkin/b3-propagation"
  (:require [zipkin-clj.core :as core]))


;; ====================================================================
;; Internal
;; ====================================================================


(defn- parse-flags [^String s]
  (case s
    ("1" "true")
    {:sample? true}

    ("0" "false")
    {:sample? false}

    "d"
    {:debug? true}

    nil))


;; ====================================================================
;; API
;; ====================================================================


(defn encode
  "Encodes `span` for propagation in a single \"b3\" header.
  `span` must be a valid span, e.g., produced by `zipkin-clj.core/span`.
  Returns a string to be used as a \"b3\" header value. "
  [span]
  (let [flags (if (-> span :debug)
                "d"
                (when-some [sample? (::core/sample? span)]
                  (if sample? "1" "0")))]
    (str
      (:traceId span) "-" (:id span)
      (when flags
        (str
          "-" flags
          (when-let [parent-id (:parentId span)]
            parent-id))))))


(defn decode
  "Converts a \"b3\" header value into a span.
  Returns a span to use, e.g., with `zipkin-clj.core/trace-context!` or
  as a parent span. The span doesn't have start time and therefore is not
  supposed to be used with `zipkin-clj.core/finish-span`."
  [^String s]
  (if-some [flags (parse-flags s)]
    (core/span flags)
    (let [t-end (case (.charAt s 16) \- 16 32)
          trace-id (subs s 0 t-end)
          s-start (inc t-end)
          s-end (+ s-start 16)
          span-id (subs s s-start s-end)
          f-start (inc s-end)
          f-end (inc f-start)
          flags-str (when (> (count s) f-start) (subs s f-start f-end))
          flags (parse-flags flags-str)
          p-start (inc f-end)
          parent-id (when (> (count s) f-end) (subs s p-start))]
      (core/span
        (merge
          {:trace-id trace-id
           :id span-id}
          flags
          (when parent-id
            {:parent
             (merge
               {:traceId trace-id
                :id parent-id}
               flags)}))))))
