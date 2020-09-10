(ns learning-datomic.s2-client-api
  (:require
    [datomic.client.api :as d]))

(def cfg {:server-type :dev-local
          :system      "datomic-samples"})

(def movie-schema [{:db/ident       :movie/title
                    :db/valueType   :db.type/string
                    :db/cardinality :db.cardinality/one
                    :db/doc         "The title of the movie"}

                   {:db/ident       :movie/genre
                    :db/valueType   :db.type/string
                    :db/cardinality :db.cardinality/one
                    :db/doc         "The genre of the movie"}

                   {:db/ident       :movie/release-year
                    :db/valueType   :db.type/long
                    :db/cardinality :db.cardinality/one
                    :db/doc         "The year the movie was released in theaters"}])

(def first-movies [{:movie/title        "The Goonies"
                    :movie/genre        "action/adventure"
                    :movie/release-year 1985}
                   {:movie/title        "Commando"
                    :movie/genre        "thriller/action"
                    :movie/release-year 1985}
                   {:movie/title        "Repo Man"
                    :movie/genre        "punk dystopia"
                    :movie/release-year 1984}])

(def all-movies-q '[:find ?e
                    :where [?e :movie/title]])

(def all-titles-q '[:find ?movie-title
                    :where [_ :movie/title ?movie-title]])

(comment
  (do ;; evaluate all at once for convenience
    (def client (d/client cfg))
    (d/create-database client {:db-name "movies"})
    (def conn (d/connect client {:db-name "movies"})))
  ;; => #'learning-datomic.client-api/conn

  (d/transact conn {:tx-data movie-schema})
  ;; numerics below will vary in your results...
  #_ {:db-before #datomic.core.db.Db{:id "movies",
                                  :basisT 5,
                                  :indexBasisT 0,
                                  :index-root-id nil,
                                  :asOfT nil,
                                  :sinceT nil,
                                  :raw nil},
   :db-after #datomic.core.db.Db{:id "movies",
                                 :basisT 6,
                                 :indexBasisT 0,
                                 :index-root-id nil,
                                 :asOfT nil,
                                 :sinceT nil,
                                 :raw nil},
   :tx-data [#datom[13194139533318 50 #inst"2020-08-18T18:57:29.557-00:00" 13194139533318 true]
             #datom[73 10 :movie/title 13194139533318 true]
             #datom[73 40 23 13194139533318 true]
             #datom[73 41 35 13194139533318 true]
             #datom[73 63 "The title of the movie" 13194139533318 true]
             #datom[74 10 :movie/genre 13194139533318 true]
             #datom[74 40 23 13194139533318 true]
             #datom[74 41 35 13194139533318 true]
             #datom[74 63 "The genre of the movie" 13194139533318 true]
             #datom[75 10 :movie/release-year 13194139533318 true]
             #datom[75 40 22 13194139533318 true]
             #datom[75 41 35 13194139533318 true]
             #datom[75 63 "The year the movie was released in theaters" 13194139533318 true]
             #datom[0 13 73 13194139533318 true]
             #datom[0 13 74 13194139533318 true]
             #datom[0 13 75 13194139533318 true]],
   :tempids {}}

  (d/transact conn {:tx-data first-movies})
  ;; numerics below will vary in your results...
  #_ {:db-before #datomic.core.db.Db{:id "movies",
                                     :basisT 6,
                                     :indexBasisT 0,
                                     :index-root-id nil,
                                     :asOfT nil,
                                     :sinceT nil,
                                     :raw nil},
      :db-after #datomic.core.db.Db{:id "movies",
                                    :basisT 7,
                                    :indexBasisT 0,
                                    :index-root-id nil,
                                    :asOfT nil,
                                    :sinceT nil,
                                    :raw nil},
      :tx-data [#datom[13194139533319 50 #inst"2020-08-18T18:58:29.477-00:00" 13194139533319 true]
                #datom[101155069755468 73 "The Goonies" 13194139533319 true]
                #datom[101155069755468 74 "action/adventure" 13194139533319 true]
                #datom[101155069755468 75 1985 13194139533319 true]
                #datom[101155069755469 73 "Commando" 13194139533319 true]
                #datom[101155069755469 74 "thriller/action" 13194139533319 true]
                #datom[101155069755469 75 1985 13194139533319 true]
                #datom[101155069755470 73 "Repo Man" 13194139533319 true]
                #datom[101155069755470 74 "punk dystopia" 13194139533319 true]
                #datom[101155069755470 75 1984 13194139533319 true]],
      :tempids {}}

  (d/transact conn {:tx-data [{:db/ident       :movie/rating
                               :db/valueType   :db.type/string
                               :db/cardinality :db.cardinality/one
                               :db/doc         "The rating of the movie"}]})
  ;; numerics below will vary in your results...
  #_ {:db-before #datomic.core.db.Db{:id "movies",
                                  :basisT 8,
                                  :indexBasisT 0,
                                  :index-root-id nil,
                                  :asOfT nil,
                                  :sinceT nil,
                                  :raw nil},
   :db-after #datomic.core.db.Db{:id "movies",
                                 :basisT 9,
                                 :indexBasisT 0,
                                 :index-root-id nil,
                                 :asOfT nil,
                                 :sinceT nil,
                                 :raw nil},
   :tx-data [#datom[13194139533321 50 #inst"2020-08-18T18:59:13.333-00:00" 13194139533321 true]],
   :tempids {}}

  (d/transact conn {:tx-data [{:movie/title        "Caddy Shack II"
                               :movie/rating       "PG"
                               :movie/genre        "comedy/sports"
                               :movie/release-year 1986}]})
  ;; numerics below will vary...
  #_ {:db-before #datomic.core.db.Db{:id "movies",
                                  :basisT 9,
                                  :indexBasisT 0,
                                  :index-root-id nil,
                                  :asOfT nil,
                                  :sinceT nil,
                                  :raw nil},
   :db-after #datomic.core.db.Db{:id "movies",
                                 :basisT 10,
                                 :indexBasisT 0,
                                 :index-root-id nil,
                                 :asOfT nil,
                                 :sinceT nil,
                                 :raw nil},
   :tx-data [#datom[13194139533322 50 #inst"2020-08-18T18:59:56.663-00:00" 13194139533322 true]
             #datom[96757023244367 73 "Caddy Shack II" 13194139533322 true]
             #datom[96757023244367 76 "PG" 13194139533322 true]
             #datom[96757023244367 74 "comedy/sports" 13194139533322 true]
             #datom[96757023244367 75 1986 13194139533322 true]],
   :tempids {}}

  (def db (d/db conn))
  ;; => #'learning-datomic.client-api/db
  ;; but we will not use this ^^^
  ;; we will query by pulling the current db from the connection each time

  (d/q all-movies-q (d/db conn))
  ;; numerics will vary
  ;; => [[101155069755468] [101155069755469] [101155069755470] [96757023244367]]

  (d/q all-titles-q (d/db conn))
  ;; => [["Caddy Shack II"] ["Commando"] ["The Goonies"] ["Repo Man"]]

  (def titles-from-1985 '[:find ?title
                          :where [?e :movie/title ?title]
                          [?e :movie/release-year 1985]])
  ;; => #'learning-datomic.client-api/titles-from-1985

  (d/q titles-from-1985 (d/db conn))
  ;; => [["Commando"] ["The Goonies"]]


  (def all-data-from-1985 '[:find ?title ?year ?genre
                            :where [?e :movie/title ?title]
                            [?e :movie/release-year ?year]
                            [?e :movie/genre ?genre]
                            [?e :movie/release-year 1985]])
  ;; => #'learning-datomic.client-api/all-data-from-1985

  (d/q all-data-from-1985 (d/db conn))
  ;; => [["Commando" 1985 "thriller/action"] ["The Goonies" 1985 "action/adventure"]]


  (d/q '[:find ?e
         :where [?e :movie/title "Commando"]]
    (d/db conn))
  ;; [[101155069755469]]

  ;; optional...
  (d/delete-database client {:db-name "movies"})
  ;; => true
  )