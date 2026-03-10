(ns ol.trixnity.store.h2
  (:import
   [javax.sql DataSource]
   [org.h2.jdbcx JdbcDataSource]
   [org.jetbrains.exposed.sql Database]))

(set! *warn-on-reflection* true)

(defn make-datasource
  [filename]
  (let [ds (JdbcDataSource.)]
    (.setURL ds (str "jdbc:h2:file:" filename ";DB_CLOSE_DELAY=-1;"))
    (.setUser ds "")
    (.setPassword ds "")
    ds))

(defn connect-exposed
  [filename]
  (org.jetbrains.exposed.sql.Database$Companion/connect$default
   Database/Companion
   ^DataSource (make-datasource filename)
   nil
   nil
   nil
   nil
   30
   nil))
