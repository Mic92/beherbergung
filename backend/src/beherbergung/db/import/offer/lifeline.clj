(ns beherbergung.db.import.offer.lifeline
  (:require [beherbergung.config.state :refer [env]]
            [beherbergung.db.state :refer [db_ctx]]
            [beherbergung.auth.uuid.core :refer [uuid]]
            [beherbergung.model.offer-mapping.core :refer [unify]]
            [beherbergung.model.offer-mapping.lifeline]
            [beherbergung.model.offer :as offer]
            [beherbergung.model.ngo :as ngo]
            [clojure.edn]))

(defn geocode
  [record]
  (assoc record
         :place_lon  12.34
         :place_lat 51.34))

(defn update-offers [table]
  (let [ngo:id "lifeline_beherbergung"  ;; TODO
        {:keys [tx-fn-put tx-fn-call]} db_ctx]
       (tx-fn-put :update-offer
                  '(fn [ctx eid doc ngo:id]
                       (let [db (xtdb.api/db ctx)
                             entity (xtdb.api/entity db eid)]
                            [[:xtdb.api/put (assoc (merge entity doc)
                                                   :xt/id eid
                                                   :xt/spec ::offer/record
                                                   ::ngo/id ngo:id)]])))
       (doseq [record table
               :let [existingId (when (:id_tmp record)
                                      (str "offer_" (:id_tmp record)))]]
              (tx-fn-call :update-offer (or existingId (uuid))
                                        (geocode record)
                                        ngo:id))))

(defn import! []
  (let [table (if (:import-file env)
                  (unify (clojure.edn/read-string (slurp (:import-file env)))
                         beherbergung.model.offer-mapping.lifeline/mapping)
                  (clojure.edn/read-string (slurp "./data/import/example.edn"))  ;; till conflict between `specialist-server.type` and `with-gen` is fixed
                  #_(gen/sample (s/gen ::offer)))]
       (println "Records to be imported:" (count table))
       (update-offers table))
  (println "imported :)"))

(comment
  (import!)

  (let [ngo:id "lifeline_beherbergung"
        {:keys [q_unary]} db_ctx]
       (-> (q_unary '{:find [(pull ?e [*])]
                      :where [[?e :xt/spec ::offer/record]
                              [?e ::ngo/id ngo:id]]
                      :in [ngo:id]}
                    ngo:id))))
