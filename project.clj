(defproject zipkin-clj "0.3.0"
  :description "Zipkin instrumentation library for Clojure."
  :url "https://github.com/suprematic/zipkin-clj"
  :license
  {:name "Eclipse Public License - v1.0"
   :url  "https://www.eclipse.org/legal/epl-v10.html"}

  :dependencies
  [[org.clojure/clojure "1.9.0"]]

  :profiles
  {:dev
   {:global-vars
    {*warn-on-reflection* true}}

   :example
   {:dependencies
    [[nrepl/nrepl "0.6.0"]
     [clj-http "3.9.1"]
     [cheshire "5.6.3"]]
    :source-paths ["src" "example"]
    :repl-options {:init-ns zipkin-clj.example}
    :jvm-opts ["-Dzipkin.url=http://localhost:9411"]}

   :test
   {:dependencies
    [[org.clojure/core.match "0.3.0-alpha5"]]}})
