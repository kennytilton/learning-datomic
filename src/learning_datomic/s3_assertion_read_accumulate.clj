(ns dto-play.s3-assertion-read-accumulate
  (:require
    [datomic.client.api :as d]))

;; first, evaluate the above to establish CLJ namespace and requirements

(comment ;; the ASSERTION section: https://docs.datomic.com/cloud/tutorial/assertion.html
  (def cfg {:server-type :dev-local
            :system "datomic-samples"})
  (do
    (def client (d/client cfg))
    (d/create-database client {:db-name "tutorial"})
    (def conn (d/connect client {:db-name "tutorial"})))
  ;=> #'dto-play.assertion/conn

  (d/transact
    conn
    {:tx-data [{:db/ident :red}
               {:db/ident :green}
               {:db/ident :blue}
               {:db/ident :yellow}]})

  (defn make-idents
    [x]
    (mapv #(hash-map :db/ident %) x))

  (def sizes [:small :medium :large :xlarge])
  (make-idents sizes)

  (def types [:shirt :pants :dress :hat])
  (def colors [:red :green :blue :yellow])

  (d/transact conn {:tx-data (make-idents sizes)})
  (d/transact conn {:tx-data (make-idents colors)})
  (d/transact conn {:tx-data (make-idents types)})

  (def schema-1
    [{:db/ident       :inv/sku
      :db/valueType   :db.type/string
      :db/unique      :db.unique/identity
      :db/cardinality :db.cardinality/one}
     {:db/ident       :inv/color
      :db/valueType   :db.type/ref
      :db/cardinality :db.cardinality/one}
     {:db/ident       :inv/size
      :db/valueType   :db.type/ref
      :db/cardinality :db.cardinality/one}
     {:db/ident       :inv/type
      :db/valueType   :db.type/ref
      :db/cardinality :db.cardinality/one}])

  (d/transact conn {:tx-data schema-1})

  (defn create-sample-data
    [colors sizes types]
    "Create a vector of maps of all permutations of args"
    (->> (for [color colors size sizes type types]
           {:inv/color color
            :inv/size  size
            :inv/type  type})
      (map-indexed
        (fn [idx map]
          (assoc map :inv/sku (str "SKU-" idx))))
      vec))

  @(def sample-data (create-sample-data colors sizes types))

  (d/transact conn {:tx-data sample-data})
  ) ;; end of ASSERTION section

(comment ;; the READ section of the turtorial: https://docs.datomic.com/cloud/tutorial/read.html
  (def db (d/db conn))

  (d/pull
    db
    [{:inv/color [:db/ident]}
     {:inv/size [:db/ident]}
     {:inv/type [:db/ident]}]
    [:inv/sku "SKU-42"])

  (d/q
    '[:find ?sku ?color
      :where
      [?e :inv/sku "SKU-42"]
      [?e :inv/color ?color]
      [?e2 :inv/color ?color]
      [?e2 :inv/sku ?sku]]
    db)
  ) ;; end of READ section of tutotiral

(comment ;; the ACCUMULATE tutorial: https://docs.datomic.com/cloud/tutorial/accumulate.html
  (def order-schema
    [{:db/ident :order/items
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/many
      :db/isComponent true}
     {:db/ident :item/id
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/one}
     {:db/ident :item/count
      :db/valueType :db.type/long
      :db/cardinality :db.cardinality/one}])
  (d/transact conn {:tx-data order-schema})

  (def add-order
    {:order/items
     [{:item/id [:inv/sku "SKU-25"]
       :item/count 10}
      {:item/id [:inv/sku "SKU-26"]
       :item/count 20}]})

  (d/transact conn {:tx-data [add-order]})

  )