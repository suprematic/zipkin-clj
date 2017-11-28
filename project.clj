(defproject zipkin-clj "0.1.0-SNAPSHOT"
  :license "Proprietary closed source"
  :url "http://www.suprematic.net"
  :description "Zipkin instrumentation library for Clojure."

  :plugins [[lein-ancient "0.6.10"]
            [s3-wagon-private "1.3.0"]]

  :dependencies [[org.clojure/clojure "1.8.0"]]
  :target-path "target/%s"
  :profiles {:test {:dependencies [[org.clojure/core.match "0.3.0-alpha5"]]}
             :uberjar {:aot :all}}

  :repositories [["snapshots" {:url "s3p://suprematic/mvn/snapshots/"
                               :no-auth true}]
                 ["releases" {:url "s3p://suprematic/mvn/releases/"
                              :no-auth true
                              :sign-releases false}]])
