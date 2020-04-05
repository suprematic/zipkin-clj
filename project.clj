(defproject zipkin-clj "0.1.1-SNAPSHOT"
  :description "Zipkin instrumentation library for Clojure."
  :url "https://github.com/suprematic/zipkin-clj"
  :license
  {:name "Eclipse Public License - v1.0"
   :url  "https://www.eclipse.org/legal/epl-v10.html"}


  :dependencies [[org.clojure/clojure "1.8.0"]]
  :target-path "target/%s"
  :profiles {:test {:dependencies [[org.clojure/core.match "0.3.0-alpha5"]]}
             :uberjar {:aot :all}})

