(ns spothist.db)

(def default-db
  {:status nil
   :keypair nil
   :data-fetched? false
   :data-fetch-progress nil ;; int (events fetched) when fetching
   :data-fetch-error nil
   :data-schema ""
   :current-route nil
   :query-running? false
   :query-result nil
   :query-error nil})

