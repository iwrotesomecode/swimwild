(ns iwrotesomecode.db
  (:require [hugsql.core :as hugsql]))

(def config
  {:classname "org.postresql.Driver"
   :subprotocol "postgresql"
   :subname (str "//localhost:5432/" (System/getenv "POSTGRES_DB"))
   :user (System/getenv "POSTGRES_USER")
   :password (System/getenv "POSTGRES_PASSWORD")})

(hugsql/def-db-fns "swimwild.sql")

(defn init-db [config]
  (create-table-stations config)
  (create-table-water config)
  (create-table-rev config)
  (create-table-weather config)
  (create-table-precipitation config))

(comment
  (init-db config))
