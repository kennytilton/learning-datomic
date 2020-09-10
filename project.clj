(defproject learning-datomic "0.1.0-SNAPSHOT"
  :description "Learning Datomic with code"
  :url "https://github.com/kennytilton"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/java.data "1.0.86"]
                 [com.datomic/dev-local "0.9.184"]
                 [com.datomic/client-cloud "0.8.102"]]
  :main ^:skip-aot dto-play.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
