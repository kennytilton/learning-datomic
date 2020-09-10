(ns learning-datomic.splitting-datomic
  (:require
    [datomic.client.api :as d]
    [clojure.java.data :as j]))

;; I like magic, and I like magical interfaces, but I do not
;; like magic in programming interfaces until I understand how it works.
;;
;; I am not smart like you all; I think better in concrete. I need concrete.
;; Hence my approach here to datomic, and the title, "Splitting Datomic".
;; We will start at the core of datomic and learn the magic once the concrete has set.
;;
;; First, a reminder of the big picture: a datomic db is a set of facts,
;; where each fact consists, loosely speaking, of a noun, verb, and object.
;; e.g., [ken plays tennis]. More strictly, in dto we have entity, attribute, and value.
;; If you know RDF, these would be subject, predicate, and object.
;;
;; RDF facts commonly also have a so-called "graph" property, which can be used
;; to group facts in a sub-graph. In a graph db such as Allegrograph, we also
;; have an identity property unique to each fact, so facts can describe facts.
;; A classic example here is the fact "fact-42 has-confidence-level 93%"
;;
;; Datomic enjoys its own extras. First, a boolean OP that indicates if a fact
;; still holds. i.e, a fact such as [kenny attends clark-univ] can remain in
;; the DB but be "retracted" when he drops out, leaving it as false.
;;
;; Second, every datom has a TX (for transaction) property holding the ID of
;; the ACID transaction
;; that added the fact (datom) to the DB. Altogether, then, a datom has five
;; properties:
;;
;;   Shorthand Property        Remarks
;;       E      entity         self, this, an RDB/SQL row, a map...
;;       A      attribute      RDF predicate, property-value map property...
;;       V      value
;;       Tx     transaction ID (Datomic is ACID. Yay.)
;;       Op     T/F: Is the fact true (or has it been retracted)
;;
;;  Gripe: "Op" is short for "operation", so why is it a boolean?
;;  Well, we are asked to mentally translate true to the operation
;; "add fact" and false to "retract fact". But does that work?
;;  Once the operation completes, we still have the value
;;  at rest as part of the datom. A better name for the property might be "holds?",
;;  in the sense of whether an assertion still holds or not. Perhaps more
;;  confusing (or simpler!) would be to call the property "true?". This might be
;;  confuding because nothing says a datom fact is in fact true or false--the property just signifies
;;  whether we have added or retracted it! And guess what?
;;
;;  The java property name is "added": https://docs.datomic.com/on-prem/javadoc/datomic/Datom.html
;;  We will try sticking with "Op" because it appears in the Datomic doc.
;;
;;       Op     T/F: whether the datom is being added or retracted?
;;
;; And in defense of that terminology, it can be very helpful when looking at the results of
;; of a transaction in which we *change* the value of a datom. We cannot change
;; a datom, really,
;; because Datomic is an immutable DB. In the Tx log of what we think of
;; as a change we will see the "before" version of the datom with OP=false
;; indicating that it is being retracted, and the new version with OP=true
;; indicating that it is being added. Thanks to ACID, the world
;; will simply see the fact change, ie, take on the new value in the TX.
;;
;; Fun note: we can flip the Op of a retracted fact back to true? It will be fun
;; to see how dto handles this.
;;
;; Fun question: how can we see retracted facts?
;;
;; Now on with the show. To begin, we need a database. We do this in three steps:
;;   1. create a client describing the Datomic server;
;;   3. tell the server to make a database  using over that connection.
;;   2. create a connection using that client spec;

(comment
  (def client (d/client {:server-type :dev-local
                         :system      "datomic-samples"}))
  (d/create-database client {:db-name "splitting"})
  (def conn (d/connect client {:db-name "splitting"})))
;;
;; Now let's read the DB. You might ask, read what? A new DB should be empty.
;;
;; Think "metadata", as with Postgres system catalogs: https://www.postgresql.org/docs/9.1/catalogs.html
;; We will not understand much, but at least we will learn the rawest way
;; to read a dto db, viz., the function datoms in the dto client API, specifically
;; the so-called "index api": https://docs.datomic.com/on-prem/indexes.html.
;; datoms reads the very indexes of dto, where by the way the datoms actually
;; reside (no further lookup). Apparently these are known as "covering indexes".
;;
;; On with the read!

(comment
  (d/datoms (d/db conn) {:index :eavt}))

;(#datom[0 10 :db.part/db 13194139533312 true]
; #datom[0 11 0 13194139533313 true]
; #datom[0 11 3 13194139533312 true]
; ...)

;; wow. how many was that?

(comment
  (count (d/datoms (d/db conn) {:index :eavt})))

;; A new DB has 217 datoms. OK. So what does {:index :eavt} do?
;; That tells the function datoms to use the
;; index sorted on E, A, V, and Tx. If we know the entity E we are after,
;; this is the fastest way to get to it.
;;
;; Other indexes sort differently and are faster depending what info
;; we are starting with. AEVT sorts by attribute and entity, faster where
;; we want information under an attribute.
;;
;; More useful and more expensive ia AVET, where we can quickly find all
;; entities that, say, "plays tennis". Because this is expensive, dto does
;; not index a given attribute such as "plays" by default. Just a little down
;; the road we will learn how to ask.
;;
;; todo: what if we ask about an attribute not indexed AVET? Is it just slow, or
;; does it return nothing?
;;
;; Back to our datoms:
; (#datom[0 10 :db.part/db 13194139533312 true]
; #datom[0 11 0 13194139533313 true]
; #datom[0 11 3 13194139533312 true]
; ....
; #datom[0 13 68 13194139533317 true]
; #datom[0 13 69 13194139533317 true]
; #datom[0 13 70 13194139533317 true]
; #datom[0 13 71 13194139533317 true]
; #datom[0 13 72 13194139533317 true]
; #datom[0
;        63
;        "Name of the system partition. The system partition includes the core of datomic, as well as user schemas: type definitions, attribute definitions, partition definitions, and data function definitions."
;        13194139533315
;        true]
; #datom[1 10 :db/add 13194139533312 true]
; ...)
;;
;; Lots of numbers. Hmmm. Tx is always a number. That is an ID that is fine, but they
;; do not come out in order. We are hacking here, so lets see them in order and grouped
;; while we are at it. Next.
;;
;; Entities are all numbers. No surprise there to AllegroGraph fans. As Franz's John
;; Foderaro famously noted:
;;
;;   "All five of the four parts of a triple are implemented as 64-bit integers."
;;
;; Nice to see the entity ID zero. It is not, after all, entities all the way down.
;;
;; Curiously, all the attributes are numbers. Where are "plays" and "likes"? Answering
;; that will take us pretty deep, but we get there soon. And we have tipped our
;; hand with the Foderaro quote.
;;
;; Many values are also numbers, suspiciously many, so we will look at that soon, too.
;; But we do see a nice readable string "Name of system partition." and two mysterious
;; namespaced keywords, :db.part/db and :db/add.
;;
;; Note: "db" is a namespace reserved for dto. Do not mess with "db".
;; Note 2: "db.part/" demonstrates hierarchical namespacing.
;; todo: is hierarchical NS just for the reader's eyes, or does it recognized
;; somehow by dto/clojure?
;;
;; Looking at the string and the namespace, guessing :db.part is short for
;; :db.partition.
;;
;; Finally all the "ops" are true, which makes sense for a DB at creation.
;;
;; now lets see the primordial datoms in the order of creation, grouped by
;; transaction so we can get a feel for the construction of a DB from scratch.
;;

(comment
  (group-by :tx
    (d/datoms (d/db conn) {:index :eavt})))

;; Cool, we can see the bootstrapping of a database. Just for fun, let's
;; zoom in on two facts:
;
; #datom[50 10 :db/txInstant 13194139533312 true]
; #datom[13194139533312
;        50
;        #inst"1970-01-01T00:00:00.000-00:00"
;        13194139533312
;        true]]
;;
;; Ah, a fact about entity 13194139533312 is created during Tx 13194139533312.
;; That fact's attribute is 50. (?!) The value is a Java instant in time.
;;
;; So what is 50? Look again at the first fact:
;;
;;   #datom[50 10 :db/txInstant 13194139533312 true]
;;
;; Here ^^^, 50 is the entity! The attribute is a mysterious 10, but
;; the value is :db/txInstant. We look through the output and find a
;; datom where the first element, te entity E, is 10:
;
;    #datom[10 10 :db/ident 13194139533312 true]
;
;; We are definitely in bootstrap-land, turtle atop turtle, and we are ahead
;; of ourselves, but we do want to split Datomic. We just learned that the
;; entity 10 has keyword :db/ident as the value of attribute 10.
;;
;; My head is spinning, too, like when I first learned that an OO instance's class
;; was an instance of a (meta)class in the Common Lisp meta-object protocol.
;; [see http://cse.unl.edu/cl/mop/contents.html]. But I digress. Let's get back to 10.
;;
;; Allow me to translate #datom [10 10 :db/ident 13194139533312 true].
;;
;;    There is a Datomic identifier, itself named 'ident', in the
;;    crucial namespace 'db'. Identifiers, by agreement, are established
;;    by facts where :db/ident is the attribute. Because we cannot have
;;    turtles all the way down, DB creation begins with an arbitrary choice
;;    of an integer for :db/ident, in this case 10. We then create the identifier
;;    with the fact [10 10 :db/ident], saying in effect "10 will be the entity ID
;;    of the identifier :db/ident. But 10 in the attribute position means
;;    10 must be an attribute, meaning 10 must have attributes used to
;;    define attributes. [todo: show these] [todo:create an ident :nota/attribute
;;    and confirm dto will not accept that.]
;;    So we also have an attribute named :db/ident. [todo: show the bootstrapping
;;    of the :db/ident attribute.]
;;    The attribute :db/ident establishes an identifier whose symbolic name
;;    is the value of the fact with :db/ident as the attribute name.
;;
;; Translated, 13194139533312 is a dto transaction. One attribute of that is
;; an inscrutable 50, but we see 50 has a helpful identifier, :db/txInstant.
;; And tx 13194139533312's :db/txInstant is #inst"1970-01-01T00:00:00.000-00:00".
;;
;; Hmmm. That is the epoch, the beginning of time. All the txs have that
;; attribute/value pair.
;; todo does :db/txInstant mean "instance of a Tx" or time instance?
;;
;; We are too deep and narcosis approaches, but before surfacing let us see what
;; other identifiers we will find in the Datomic attribute language, and
;; learn a bit more of the index api syntax:

(comment
  (map (juxt :e :v)
    (d/datoms (d/db conn)
      {:index      :aevt
       :components [10]})))

;; "components" are supplied in the order shown in the index identifier.
;; Let us look for 50 again:

(comment
  (d/datoms (d/db conn)
    {:index      :aevt
     :components [10 50]}))

; >> (#datom[50 10 :db/txInstant 13194139533312 true])
;;
;; Can we omit a component?

(comment
  (d/datoms (d/db conn)
    {:index      :aevt
     :components [_ 50]})
  ;; Syntax error: Unable to resolve symbol: _ in this context

  (d/datoms (d/db conn)
    {:index      :aevt
     :components [nil 50]})
  ;; > ())
  )

;; No, but this is why we have the alternate indexes. If all we know is the
;; entity:
(comment
  (d/datoms (d/db conn)
    {:index      :eavt
     :components [50]}))

;(#datom[50 10 :db/txInstant 13194139533312 true]
; #datom[50 40 25 13194139533313 true]
; #datom[50 41 35 13194139533313 true]
; #datom[50
;        63
;        "Attribute whose value is a :db.type/instant. A :db/txInstant is recorded automatically with every transaction."
;        13194139533315
;        true])
;;
;; Hmmm. Are we tired of looking at numbers yet? It seems we have a way to
;; determine the keyword identifier associated with a raw, numeric attribute A by
;; looking for the value V of the fact where E is A and the attribute is 10.
;; Let's see if 63 expands to :db/docstring or sth.

(comment
  (d/datoms (d/db conn)
    {:index      :eavt
     :components [63 10]}))

; > (#datom[63 10 :db/doc 13194139533314 true])
;;
;; Close enough! Hmmm. Do we have to say "10"? Why do we have identifiers?!

(comment
  (d/datoms (d/db conn)
    {:index      :eavt
     :components [63 :db/ident]}))

;; Booya! (#datom[63 10 :db/doc 13194139533314 true])
;;
;; Datomic confirms its Lisp heritage by first and foremost
;; being a symbolic database.

;; Let's make our first utility.

(def ^:dynamic *db* (d/db conn))                            ;; <---- re-evaluate as needed!!!!!!!!!!!

(defn id-to-ident [id]
  (:v (first
        (d/datoms *db*
          {:index      :eavt
           :components [id :db/ident]}))))

(defn ident-to-id [ident]
  (:e (first
        (d/datoms *db*
          {:index      :avet
           :components [:db/ident ident]}))))

(comment
  (id-to-ident 10)
  ;=> :db/ident
  ;; If *db* is not defined properly at the top level:
  ;;    ; No implementation of method: :datoms of protocol: #'datomic.client.api.protocols/Db found for class: nil

  (ident-to-id :db/ident)

  ;; Super. We have set this up as a classic application of special variables: in
  ;; a dynamic call tree every use of the DB should reference the same DB, unless
  ;; heroic measures--ie; (binding ...) are taken to designate another DB as the default DB.
  ;; BUT! We do not want to be forever passing around the DB as a function parameter.
  ;;
  ;; We will be using the global conn and db/conn for most examples, but when we
  ;; finish with some DB time travel, we will want these core utilities to be on the same
  ;; page, accessing the same DB. Hence our ^:dynamic *db*

  )

;; what if we do not find an ident? When we get to values, a number
;; might just be a number.

(defn maybe-id-to-ident [id]
  (or (id-to-ident id)
    id))

(comment
  (id-to-ident 63)                                          ;; :db/doc
  (let [{:keys [e a v tx added]} (first (d/datoms (d/db conn)
                                          {:index      :eavt
                                           :components [63 :db/ident]}))]
    [e a v tx added])
  (d/datoms (d/db conn)
    {:index      :eavt
     :components [63]}))

(defn datoms-raw
  "Datoms with values translated to idents where possible"
  [index-id & components]
  (map (fn [{:keys [e a v tx added]}]
         [e a v tx added])
    (d/datoms *db*
      {:index      index-id
       :components components})))

(comment
  (ident-to-id :db/txInstant)
  (d/datoms (d/db conn)
    {:index      :eavt
     :components nil})

  (d/datoms (d/as-of (d/db conn) 13194139533312)
    {:index      :aevt
     :components [50]}))

(defn datoms-xlt
  "Datoms with values translated to idents where possible"
  [index-id & components]
  (map (fn [{:keys [e a v tx added]}]
         [(maybe-id-to-ident e)
          (maybe-id-to-ident a)                             ;; todo maybe?
          (maybe-id-to-ident v)
          tx added])
    (d/datoms *db*
      {:index      index-id
       :components components})))

(comment
  ;; let's learn everything we can about :db/doc
  (datoms-xlt :eavt 63)
  ;; and we learned the index API at least will let us get away with the ident :db/doc
  (datoms-xlt :eavt :db/doc)

  ;; so we are translating E to idents, falling back on the number E if
  ;; we do not find a translation. Do we ever not find a translation?
  (filter (comp number? #(nth % 0))
    (datoms-xlt :aevt))
  ; ([13194139533312
  ;  :db/txInstant
  ;  #inst"1970-01-01T00:00:00.000-00:00"
  ;  13194139533312
  ;  true] ... and four more :db/txInstant's)
  ;
  ;; Aha! Only our transactions have true, meaningless  entity  identifiers,
  ;; generated by a mechanism we have not seen, defined only by
  ;; the set of facts sharing the same entity ID.
  ;;
  ;; How about attributes? Do they always translate?
  (filter (comp number? #(nth % 1))
    (datoms-xlt :aevt))
  ; -> () ;; yep
  ;;
  ;; We know we get strings as values for :db/doc. Do we ever get
  ;; untranslated numbers?

  (filter (comp number? #(nth % 2))
    (datoms-xlt :aevt))
  ; -> () ;; nope, not yet

  ;; One last thing before we surface. I just realized we have not
  ;; looked at the big bang, the first turtle, the origin, in the
  ;; beginnng was void...

  (id-to-ident 0)                                           ;=> :db.part/db
  ;; more fulsomely...
  [0 :db/ident :db.part/db 13194139533312 true]

  ;; hmmm. when did :db/ident arise...
  (datoms-xlt :vaet :db/ident)
  ;=> ([:db.part/db :db.install/attribute :db/ident 13194139533313 true])

  ;; "Install attribute"! Bootstrapping indeed.
  ;; Hmmm. And :db.install/attribute?
  (datoms-xlt :vaet :db.install/attribute)
  ([:db.part/db :db.install/attribute :db.install/attribute 13194139533313 true])

  ;;
  (ident-to-id :db.install/attribute)                       ;=> 13
  (ident-to-id :db/ident)                                   ;=> 10
  ;; now...
  (datoms-xlt :vaet 10)
  ([:db.part/db :db.install/attribute :db/ident 13194139533313 true])
  (datoms-raw :eavt 10)
  ;([10 10 :db/ident 13194139533312 true]
  ; [10 40 21 13194139533313 true]
  ; [10 41 35 13194139533313 true]
  ; [10 42 38 13194139533313 true]
  ; [10 63 "Attribute used to uniquely name an entity." 13194139533315 true])
  (datoms-raw :eavt 13)
  ;([13 10 :db.install/attribute 13194139533312 true]
  ; [13 40 20 13194139533313 true]
  ; [13 41 36 13194139533313 true]
  ; [13
  ;  63
  ;  "System attribute with type :db.type/ref. Asserting this attribute on :db.part/db with value v will install v as an attribute."
  ;  13194139533315
  ;  true])

  ;;; So, yes, we are in bootstrap mode
  ;;; In tx 13194139533312, 10 gets hard-coded as :db/ident, and
  ;;; 13 gets hard-coded as ident (via attribute 10) :db.install/attribute.
  ;;
  ;; In tx 13194139533313 we go to town with the infrastructure created
  ;; in the primordial Tx.
  ;;
  ;; Useful to note that in both cases above we have just one fact each during
  ;; the initial transaction, 13194139533312. That is the bootstrap, prolly
  ;; simply hard-coded facts exempted from rules about entity and attribute
  ;; values. Seeing this gave me one last (god I hope it is the last)
  ;; investigation before surfacing: instead of staring at TX ids trying
  ;; to pick out the first, why
  ;; not let Datomic be Datomic? Let us look at our new DB as of the first transaction:
  ;;
  (datoms-xlt :eavt)
  ; => ([:db.part/db :db/ident :db.part/db 13194139533312 true]
  ; [:db.part/db :db.install/partition :db.part/tx 13194139533312 true]
  ; [:db.part/db :db.install/partition :db.part/user 13194139533312 true]
  ; [:db.part/db :db.install/attribute :db/system-tx 13194139533312 true]
  ; [:db.part/db :db.install/attribute :db/excise 13194139533312 true]
  ; [:db.part/db :db.install/attribute :db.excise/attrs 13194139533312 true]
  ; [:db.part/db :db.install/attribute :db.excise/beforeT 13194139533312 true]
  ; [:db.part/db :db.install/attribute :db.excise/before 13194139533312 true]
  ; [:db.part/db :db.install/attribute :db.alter/attribute 13194139533312 true]
  ; [:db/add :db/ident :db/add 13194139533312 true]
  ; [:db/retract :db/ident :db/retract 13194139533312 true]....)

  ;; But those transalted facts belie the nature of a big bang, when there
  ;; is no first turtle. Let us look at the raw values to imagine
  ;; the DB universe as it was being created
  (binding [*db* (d/as-of (d/db conn) 13194139533312)]
    (datoms-xlt :eavt))
  ;; Ah, but as of the first Tx, many ident translations had been created
  ;; for use going forward. A better sense might be had of those early
  ;; moments of the DB big bang by not leveraging those translations, since
  ;; they would not have been available to the bootstrapping code:
  (binding [*db* (d/as-of (d/db conn) 13194139533312)]
    (datoms-raw :eavt))
  ;([0 10 :db.part/db 13194139533312 true]
  ; [0 11 3 13194139533312 true]
  ; [0 11 4 13194139533312 true]
  ; [0 13 7 13194139533312 true]
  ; [0 13 15 13194139533312 true]
  ; [0 13 16 13194139533312 true]
  ; [0 13 17 13194139533312 true]
  ; [0 13 18 13194139533312 true]
  ; [0 13 19 13194139533312 true]
  ; [1 10 :db/add 13194139533312 true]
  ; [2 10 :db/retract 13194139533312 true]
  ; [3 10 :db.part/tx 13194139533312 true]
  ; [4 10 :db.part/user 13194139533312 true]
  ; [7 10 :db/system-tx 13194139533312 true]
  ; [7 40 21 13194139533312 true]
  ; [7 41 36 13194139533312 true]
  ; [10 10 :db/ident 13194139533312 true]
  ; [11 10 :db.install/partition 13194139533312 true]
  ; [12 10 :db.install/valueType 13194139533312 true]
  ; [13 10 :db.install/attribute 13194139533312 true]
  ;
  ;; I can almos see the code now.


  )

; ([:db/txInstant :db/ident :db/txInstant 13194139533312 true]
; [:db/txInstant :db/valueType :db.type/instant 13194139533313 true]
; [:db/txInstant :db/cardinality :db.cardinality/one 13194139533313 true]
; [:db/txInstant
;  :db/doc
;  "Attribute whose value is a :db.type/instant. A :db/txInstant is recorded automatically with every transaction."
;  13194139533315
;  true])

;; Ah, my question above is answered: :tx/instant is indeed an instant in time. We also see that the
;; attribute has more properties than just it's name (its :db/ident)


;; Pro tip: :db/ident is the key to object identity in Datomic. I had assumed
;; that, with regards to the :db/ident keyword itself, that 10 was the real
;; identity, but that is not so. In the edge case of a DB "decant" and reload,
;; we might find in the reload this fact:
;
;     #datom[42 42 :db/ident 13194139533312 true]
;
;; For those of us accustomed to serial IDs in SQL databases, this takes some
;; getting used to. Mind you, our 10 here is fine as an object identifier
;; within a given load of the DB, but this is one of those times when
;; the edge case defines the truth.
;;
;; We return now to the surface, but we will soon see how often and
;; vitally this fundamental Datomic truth will matter.
;;
;;
;;
;
;(defn id-to-ident [conn id]
;  (ffirst
;    (d/q '[:find ?v
;           :in $ ?eid
;           :where [?eid :db/ident ?v]]
;      (d/db conn) id)))
;
;(defn ident-to-id [conn ident]
;  (ffirst
;    (d/q '[:find ?eid
;           :in $ ?qident
;           :where [?eid :db/ident ?qident]]
;      (d/db conn) ident)))

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
    (d/db cw) 42)                                           ;; [[:db/unique]]

  ;; I am curious what is the initial set of attributes.
  ;; dto will not let us retrieve all datoms using datalog, for some reason
  ;; lets hit the dto indices directly.
  (->> (d/datoms (d/db cw)
         {:index :avet})
    (map :a)
    set                                                     ;; and use our new function to get their aliases (idents)
    (map (partial id-to-ident cw)))                         ;; just a dozen

  (->> (d/datoms (d/db cw)
         {:index :avet})
    (take 1))

  (id-to-ident cw 10)

  ;; Curious about these magical tempids. If I use "bbb" repeatedly it resolves to the
  ;; same numerical entity-ID. Even in a new connection! Hell, even if I use
  ;; a different bogus "tempid"!!!!!!!!!
  (d/transact cw {:tx-data [[:db/add "bbbb" :db/ident :orange-ish]]})

  ;; The attribute :db/identity must be driving this. Let us look at the attribute itself

  (ident-to-id cw :db/ident)                                ;; => 10

  ;; Ok, now lets find all datoms where the entity-id is 10
  (d/datoms (d/db cw)
    {:index      :eavt
     :components [10]})

  ;; oy. unreadable, altho we can gues where the doc string is
  ;; let's translate each attribute using id-to-ident
  (map (fn [{:keys [e a v]}]
         (prn e (id-to-ident cw a) v))
    (d/datoms (d/db cw)
      {:index      :eavt
       :components [10]}))

  ;; better, but guessing those values also have idents/names
  (map (fn [{:keys [e a v]}]
         (prn e (id-to-ident cw a)
           (cond
             (number? v) (id-to-ident cw v)
             :default v)))
    (d/datoms (d/db cw)
      {:index      :eavt
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
      {:index      :eavt
       :components [10]}))

  (.tx (first (d/datoms (d/db cw)
                {:index      :eavt
                 :components [10]})))
  (let [{:keys [e a v tx added]}
        (first (d/datoms (d/db cw)
                 {:index      :eavt
                  :components [10]}))]
    (list e a v tx added))

  (j/from-java-shallow
    (first (d/datoms (d/db cw)
             {:index      :eavt
              :components [10]}))
    {:exceptions :omit})

  (j/from-java
    (first (d/datoms (d/db cw)
             {:index      :eavt
              :components [10]})))


  ;;; this does not work, because no entity has the ID 424242
  (d/transact cw {:tx-data [[:db/add 424242 :db/ident :orange-ish]]})

  ;;  :tx-data [#datom[13194139533330 50 #inst"2020-08-30T19:18:13.735-00:00" 13194139533330 true]
  ;           #datom[83562883711053 10 :orange-ish 13194139533330 true]],
  ; :tempids {"bbb" 83562883711053}}

  ;; we allowed dto to generate the entity ID by using a so-called "tempid" "foo"
  ;; dto responds with the generated id. Let's find it...
  (d/datoms (d/db cw)
    {:index      :eavt
     :components [83562883711053]})
  ;; ...and find it again as "the datom where the attribute :db/ident is :orange-ish
  (d/datoms (d/db cw)
    {:index      :avet
     :components [:db/ident :orange-ish]})

  (txd [{:db/ident       :movie/title
         :db/valueType   :db.type/string
         :db/cardinality :db.cardinality/one
         :db/doc         "The title of the movie"}])


  (ident-to-id cw :db/ident)

  (d/transact cw {:tx-data [[:db/add "bbbb" :movie/title "Goonies"]]})

  (txd [[:db/add "Hi, Mom!" :movie/title "Harvey"]])
  ;; 101155069755474
  (d/datoms (d/db cw)
    {:index      :eavt
     :components [101155069755474]})
  (d/datoms (d/db cw)
    {:index      :avet
     :components [:movie/title "Harvey"]})


  (d/q '[:find ?eid ?qtitle
         :in $ ?qtitle
         :where [?eid :movie/title ?qtitle]]
    (d/db cw) "Goonies")

  (txd [{:db/ident       :movie/actors,
         :db/valueType   :db.type/string,
         :db/cardinality :db.cardinality/many,
         :db/doc         "Actors, mkay?"}])

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