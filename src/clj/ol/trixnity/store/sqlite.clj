(ns ol.trixnity.store.sqlite
  (:import
   [javax.sql DataSource]
   [org.jetbrains.exposed.sql Database]
   [org.sqlite
    SQLiteDataSource
    SQLiteConfig
    SQLiteConfig$JournalMode
    SQLiteConfig$Pragma
    SQLiteConfig$TransactionMode]))

(set! *warn-on-reflection* true)

(def ^:private ^SQLiteConfig sqlite-config
  "SQLite config, optimised for write speed."
  (doto (SQLiteConfig.)
    (.setJournalMode SQLiteConfig$JournalMode/WAL)
    (.setPragma SQLiteConfig$Pragma/SYNCHRONOUS "NORMAL")
    (.setPragma SQLiteConfig$Pragma/BUSY_TIMEOUT "5000")
    (.setPragma SQLiteConfig$Pragma/TEMP_STORE "memory")
    (.setPragma SQLiteConfig$Pragma/PAGE_SIZE "4096")
    (.setPragma SQLiteConfig$Pragma/CACHE_SIZE "15625")
    (.setTransactionMode SQLiteConfig$TransactionMode/IMMEDIATE)
    (.setReadUncommitted true)
    ;; (.setLockingMode SQLiteConfig$LockingMode/EXCLUSIVE)
    #_(.setPragma SQLiteConfig$Pragma/MMAP_SIZE
                  (str (* 1024 1024 1024)) ;; 1G
                  )))

(defn make-sqlite-datasource
  [filename]
  (let [ds (SQLiteDataSource. sqlite-config)]
    (.setUrl ds (str "jdbc:sqlite:" filename))
    ds))

(def make-datasource
  make-sqlite-datasource)

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

(defn connect-jdbc
  [^String filename]
  (.getConnection ^SQLiteDataSource (make-datasource filename)))
