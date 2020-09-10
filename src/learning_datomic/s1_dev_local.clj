(ns dto-play.s1-dev-local
  (:require
    [datomic.client.api :as d]
    [datomic.dev-local :as dl]))

;; load this file into a repl
;; try evaluating each form in turn

(comment
  (def client (d/client {:server-type :dev-local
                         :system      "dev"}))
  ;; => #'dto-play.local-dev/client

  (d/create-database client {:db-name "ktilton"})
  (d/create-database client {:db-name "movies"})
  ;; true

  (def conn (d/connect client {:db-name "movies"}))
  ;; #'dto-play.local-dev/conn

  (def db (d/db conn))
  ;; #'dto-play.local-dev/db

  db
  ;#datomic.core.db.Db{:id "movies"
  ;                    , :basisT 5
  ;                    , :indexBasisT 0
  ;                    , :index-root-id nil
  ;                    , :asOfT nil
  ;                    , :sinceT nil
  ;                    , :raw nil}
  )

(comment
  {:server-type :cloud
   :region      "us-east-2"
   :system      "dev"
   :query-group "dev"
   :endpoint    "http://entry.dev.us-east-2.datomic.net:8182"
   :proxy-port  8182}

  (dl/import-cloud
    {:source {:system "dev"
              :db-name "ktilton"
              :server-type :cloud
              :region "us-east-2"
              :endpoint "http://entry.dev.us-east-2.datomic.net:8182"}
     :dest {:system "dev"
            :server-type :dev-local
            :db-name "ktilton"}})
  )