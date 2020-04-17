# zipkin-clj

[![Clojars Project][clojars-shield]][clojars-project]

zipkin-clj is a tracing instrumentation library producing trace information
compatible with [Zipkin API v2][zipkin-api]. See [Links][#links] to find out
more about [Zipkin][zipkin] and [Opentracing][opentracing].

## Project status

Early Access: until the version 1.0.0, the API changes with each minor (second)
version number. Bugfix (third) version number is not expected to break the API.

## Installation

[![zipkin-clj @clojars][clojars-info]][clojars-project]

## Usage

### Endpoint info

Before sending traces to Zipkin, it's essential to configure the endpoint
where the tracing events originate from.

``` clojure
(zipkin/set-endpoint! {:service-name "service@host"})
```

### Sending to Zipkin

By default trace information is discarded. In order to send it to Zipkin
(or anywhere else) one must configure the sender. The sender is just a function
taking a list of spans as an argument:

``` clojure
(ns zipkin-clj.example
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [zipkin-clj.core :as zipkin]))

(defn- http-trace-sender []
  (let [zipkin-url (str (System/getenv "ZIPKIN_URL") "/api/v2/spans")]
    (fn [spans]
      (let [json-body (json/generate-string spans)]
        (http/post zipkin-url
          {:body json-body
           :content-type :json})))))

(zipkin/set-sender! (http-trace-sender))
```

### B3 trace propagation

As of now there is only a basic support for cross-process trace propagation,
although the library gives all the required components to make , e.g., a simple
[Ring middleware][ring-middleware] function propagating trace info.

``` clojure
(ns zipkin-clj.example
  (:require [zipkin-clj.b3 :as zipkin-b3]
            [zipkin-clj.core :as zipkin]))

(-> {:span "root-span"} zipkin/start-span zipkin-b3/encode)
;; => "efb4a31f19b23694-4d211cf94a75dd99-1"

(zipkin-b3/decode "efb4a31f19b23694-4d211cf94a75dd99-1")
;; =>
;; {:id "4d211cf94a75dd99",
;;  :name nil,
;;  :zipkin-clj.core/sample? true,
;;  :annotations [],
;;  :tags {},
;;  :traceId "efb4a31f19b23694"}

(if-some [span (some-> request :headers (get "b3") zipkin-b3/decode)]
  (zipkin/trace-context! span
    (zipkin/annotate! "got request")
    (zipkin/child-trace!
      {:span "child-span"}
      (Thread/sleep 500)))
  (zipkin/trace!
    {:span "new-trace-span"}
    (Thread/sleep 500)))
```

### Span storage (advanced)

By default, zipkin-clj uses thread-local bindings to store spans.
That's how, e.g., the `child-trace!` macro or the `annotate!` function know
where to get the current span to work with.

Sometimes there already is some kind of execution context with its own
propagation mechanics (e.g., otplike's message context). In this case, it's
more convenient to store the spans using that context.

In order to do that, one needs to implement the `zipkin_clj.core.ISpanStorage`
interface. Use the [default implementation][default-span-storage] as an
example.

## Example

``` clojure
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


(defn- go-to-bed [])


(defn- wake-up [])


(defn main []
  (zipkin/trace!
    {:span "night"}
    (go-to-bed)
    (zipkin/annotate! "in-bed")
    (sleep)
    (sleep-more)
    (wake-up)
    (zipkin/annotate! "awake")))
```

Try it yourself:

1. Start zipkin on `localhost:9411`
2. Run `lein with-profile example repl` in the project directory
3. Call `(main)`
4. Find the trace in Zipkin UI.

## Links

- [Opentracing][opentracing]
- [Zipkin][zipkin]
- [B3 trace propagation][zipkin-b3]
- [Zipkin span format][zipkin-api]

## Contributing

Please use the project's GitHub issues page for all questions, ideas,
etc. Pull requests are welcome. See the project's GitHub contributors
page for the list of contributors.

This project uses the simplified [Clojure formatting style][clojure-style].


## License

Copyright © 2020 [SUPREMATIC][suprematic] and contributors.

Distributed under the [Eclipse Public License v1.0][eclipse-license].

[#links]: https://github.com/suprematic/zipkin-clj#links
[#examples]: https://github.com/suprematic/zipkin-clj#example
[suprematic]: https://suprematic.de
[opentracing]: https://opentracing.io/
[zipkin]: https://zipkin.io
[zipkin-b3]: https://github.com/openzipkin/b3-propagation
[zipkin-api]: https://zipkin.io/zipkin-api/#/default/post_spans
[clojars-shield]: https://img.shields.io/clojars/v/zipkin-clj.svg
[clojars-info]: https://clojars.org/zipkin-clj/latest-version.svg
[clojars-project]: https://clojars.org/zipkin-clj
[ring-middleware]: https://github.com/ring-clojure/ring/wiki/Concepts#middleware
[default-span-storage]: https://github.com/suprematic/zipkin-clj/blob/master/src/zipkin_clj/core.clj#L46
[clojure-style]: https://tonsky.me/blog/clojurefmt/
[eclipse-license]: https://www.eclipse.org/legal/epl-v10.html
