(defproject zipkin-clj "0.1.1-SNAPSHOT"
  :license "Proprietary closed source"
  :url "http://www.suprematic.net"
  :description "Zipkin instrumentation library for Clojure."


  :dependencies [[org.clojure/clojure "1.8.0"]]
  :target-path "target/%s"
  :profiles {:test {:dependencies [[org.clojure/core.match "0.3.0-alpha5"]]}
             :uberjar {:aot :all}}

  :repositories [["snapshots" {:url "s3p://suprematic/mvn/snapshots/"
                               :no-auth true}]
                 ["releases" {:url "s3p://suprematic/mvn/releases/"
                              :no-auth true
                              :sign-releases false}]])
