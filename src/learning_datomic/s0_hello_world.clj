(ns learning-datomic.s0-hello-world
  (:require
    [datomic.client.api :as d]
    [clojure.java.data :as j]))


(defn id-to-ident [conn id]
  (ffirst
    (d/q '[:find ?v
         :in $ ?eid
         :where [?eid :db/ident ?v]]
    (d/db conn) id)))

(defn ident-to-id [conn ident]
  (ffirst
    (d/q '[:find ?eid
           :in $ ?qident
           :where [?eid :db/ident ?qident]]
      (d/db conn) ident)))

(comment
  (do                                                       ;; evaluate all at once for convenience
    (def client (d/client {:server-type :dev-local
                           :system      "datomic-samples"}))
    (d/create-database client {:db-name "world"})
    (def cw (d/connect client {:db-name "world"}))
    (defn txd [args]
      (d/transact cw {:tx-data args}))
    (defn dqc [q & qargs]
      (apply d/q q (d/db cw) qargs)))

  ;; new DB is not empty, it is seeded with list meta entities
  ;; here we list all idents (a terrible name since it is so close to ID).
  (d/q '[:find ?eid ?v
         :in $
         :where [?eid :db/ident ?v]]
    (d/db cw))

  ;; find the ident for 42
  ;; now vailable as function id-to-ident
  (d/q '[:find ?v
         :in $ ?eid
         :where [?eid :db/ident ?v]]
    (d/db cw) 42) ;; [[:db/unique]]

  ;; I am curious what is the initial set of attributes.
  ;; dto will not let us retrieve all datoms using datalog, for some reason
  ;; lets hit the dto indices directly.
  (->> (d/datoms (d/db cw)
         {:index :avet})
    (map :a)
    set ;; and use our new function to get their aliases (idents)
    (map (partial id-to-ident cw))) ;; just a dozen

  (->> (d/datoms (d/db cw)
         {:index :avet})
    (take 1))

  (id-to-ident cw 10)

  ;; Curious about these magical tempids. If I use "bbb" repeatedly it resolves to the
  ;; same numerical entity-ID. Even in a new connection! Hell, even if I use
  ;; a different bogus "tempid"!!!!!!!!!
  (d/transact cw {:tx-data [[:db/add "bbbb" :db/ident :orange-ish]]})

  ;; The attribute :db/identity must be driving this. Let us look at the attribute itself

  (ident-to-id cw :db/ident) ;; => 10

  ;; Ok, now lets find all datoms where the entity-id is 10
  (d/datoms (d/db cw)
    {:index :eavt
     :components [10]})

  ;; oy. unreadable, altho we can gues where the doc string is
  ;; let's translate each attribute using id-to-ident
  (map (fn [{:keys [e a v]}]
         (prn e (id-to-ident cw a) v))
    (d/datoms (d/db cw)
      {:index :eavt
       :components [10]}))

  ;; better, but guessing those values also have idents/names
  (map (fn [{:keys [e a v]}]
         (prn e (id-to-ident cw a)
           (cond
             (number? v) (id-to-ident cw v)
             :default v)))
    (d/datoms (d/db cw)
      {:index :eavt
       :components [10]}))
  ;10 :db/ident :db/ident
  ;10 :db/valueType :db.type/keyword
  ;10 :db/cardinality :db.cardinality/one
  ;10 :db/unique :db.unique/identity
  ;10 :db/doc "Attribute used to uniquely name an entity."

  (map (fn [{:keys [e a v]}]
         (prn e (id-to-ident cw a)
           (cond
             (number? v) (id-to-ident cw v)
             :default v)))
    (d/datoms (d/db cw)
      {:index :eavt
       :components [10]}))

  (.tx (first (d/datoms (d/db cw)
           {:index :eavt
            :components [10]})))
  (let [{:keys [e a v tx added]}
    (first (d/datoms (d/db cw)
             {:index :eavt
              :components [10]}))]
    (list e a v tx added))

  (j/from-java-shallow
    (first (d/datoms (d/db cw)
             {:index :eavt
              :components [10]}))
    {:exceptions :omit})

  (j/from-java
    (first (d/datoms (d/db cw)
             {:index :eavt
              :components [10]})))


  ;;; this does not work, because no entity has the ID 424242
  (d/transact cw {:tx-data [[:db/add 424242 :db/ident :orange-ish]]})

  ;;  :tx-data [#datom[13194139533330 50 #inst"2020-08-30T19:18:13.735-00:00" 13194139533330 true]
  ;           #datom[83562883711053 10 :orange-ish 13194139533330 true]],
  ; :tempids {"bbb" 83562883711053}}

  ;; we allowed dto to generate the entity ID by using a so-called "tempid" "foo"
  ;; dto responds with the generated id. Let's find it...
  (d/datoms (d/db cw)
    {:index :eavt
     :components [83562883711053]})
  ;; ...and find it again as "the datom where the attribute :db/ident is :orange-ish
  (d/datoms (d/db cw)
    {:index :avet
     :components [:db/ident :orange-ish]})

  (txd [{:db/ident :movie/title
         :db/valueType :db.type/string
         :db/cardinality :db.cardinality/one
         :db/doc "The title of the movie"}])


  (ident-to-id cw :db/ident)

  (d/transact cw {:tx-data [[:db/add "bbbb" :movie/title "Goonies"]]})

  (txd [[:db/add "Hi, Mom!" :movie/title "Harvey"]])
  ;; 101155069755474
  (d/datoms (d/db cw)
    {:index :eavt
     :components [101155069755474]})
  (d/datoms (d/db cw)
    {:index :avet
     :components [:movie/title "Harvey"]})


  (d/q '[:find ?eid ?qtitle
         :in $ ?qtitle
         :where [?eid :movie/title ?qtitle]]
    (d/db cw) "Goonies")

  (txd [{:db/ident :movie/actors,
         :db/valueType :db.type/string,
         :db/cardinality :db.cardinality/many,
         :db/doc "Actors, mkay?"}])

  (d/transact cw {:tx-data [[:db/add 74766790688848 :movie/actors "Bogart"]]})
  (d/transact cw {:tx-data [[:db/add 74766790688848 :movie/actors "Bacall"]]})
  (d/transact cw {:tx-data [[:db/retract 74766790688848 :movie/actors "DeNiro"]]})
  (d/transact cw {:tx-data [[:db/add 74766790688848 :movie/title "Hud"]]})

  (d/q '[:find ?eid ?qactor
         :in $ ?eid
         :where [?eid :movie/actors ?qactor]]
    (d/db cw) 74766790688848)

  (d/q '[:find ?eid ?title
         :in $ ?eid
         :where [?eid :movie/title ?title]]
    (d/db cw) 74766790688848)



  (txd cw [[:db/add "ccc" :db/ident :skyblue]])
  ;; :tx-data [#datom[13194139533319 50 #inst"2020-08-30T00:46:09.332-00:00" 13194139533319 true]],
  ;

  (d/transact
    cw
    {:tx-data [{:db/ident :red}]})

  (d/q '[:find ?a ?v
         :in $ ?eid
         :where [?eid ?a ?v]]
    (d/db cw) 13194139533329)

  (d/q '[:find ?eid ?v
         :in $ ?eid
         :where [?eid :db/ident ?v]]
    (d/db cw) 50)

  (dqc cw '[:find ?e
            :in $ ?color
            :where [?e :db/ident ?color]]
    :limegreen)

  )

;; why no tempids returned for "xx"