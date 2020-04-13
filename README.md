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

_TODO_

``` clojure
;; TODO
```

### Span storage (advanced)

_TODO_

``` clojure
;; TODO
```

## Example

``` clojure
;; TODO
```

## Links

- [Opentracing][opentracing]
- [Zipkin][zipkin]
- [B3 trace propagation][zipkin-b3]
- [Zipkin span format][zipkin-api]

## Contributing

Please use the project's GitHub issues page for all questions, ideas,
etc. Pull requests are welcome. See the project's GitHub contributors
page for a list of contributors.

## License

Copyright © 2020 [SUPREMATIC][suprematic] and contributors.

Distributed under the Eclipse Public License v1.0.

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
