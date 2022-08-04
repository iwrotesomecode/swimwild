(ns iwrotesomecode.dataingest
  (:require [clojure.string :as str]
            [tablecloth.api :as tc]
            [clj-http.client :as client]
            [tech.v3.datatype.datetime :as datetime]
            [clojure.java.io :as io]
            [tech.v3.dataset :as ds]
            [tech.v3.dataset.sql :as ds-sql]
            [next.jdbc :as jdbc]
            [clojure.tools.trace :as trace]
            [taoensso.timbre :as timbre
             :refer [log debug info warn error spy trace report]]
            [taoensso.timbre.appenders.core :as appenders])
  (:import [java.time.format DateTimeFormatter]
           [java.time LocalTime LocalDate LocalDateTime])
  (:gen-class))

(def logfile "./log.txt")
(timbre/merge-config!
 {:appenders {:spit (appenders/spit-appender {:fname logfile})}})
;; set lowest-level output. 'spy' without level implies :debug
(timbre/set-level! :debug)
;;      :trace  = level 0
      ;; :debug  = level 1 ; Default min-level
      ;; :info   = level 2
      ;; :warn   = level 3
      ;; :error  = level 4 ; Error type
      ;; :fatal  = level 5 ; Error type
      ;; :report = level 6 ; High general-purpose (non-error) type

(def today (LocalDate/now))
(def file (str "safetoswim_2020-present_" today ".csv"))
(def url "https://data.ca.gov/dataset/surface-water-fecal-indicator-bacteria-results/resource/1987c159-ce07-47c6-8d4f-4483db6e6460/download/")
(def link (str url file))
(def now (LocalDateTime/now))
(def datafile (str "water-" today ".csv"))

(defn get-file!
  "GET request for raw csv file"
  []
  ;; only run when file doesn't exist--useful for testing/developing only
  (when (not (.exists (io/file datafile)))
    (clojure.java.io/copy
     (:body (client/get link {:as :stream}))
     (java.io.File. datafile))))

(defn data-quality-filter
  "Filter out data of poor quality and sample metadata like quality control results, duplicates, etc"
  [row]
  (and (:DataQuality row)
       (every? identity [(not= "MetaData" (:DataQuality row))
                         (not= "Reject record" (:DataQuality row))
                         (not= "Extensive review needed" (:DataQuality row))])))

(defn blank-filter
  "Filter out blanks (these include substrings BL and Blank in
  SampleTypeCode)--this should be captured by 'Metadata' filter, but not always
  coded properly"
  [row]
  (and (:SampleTypeCode row)
       (not (str/includes? (:SampleTypeCode row) "B")))) ;; Match BL and Blank

(defn future-sample-filter
  "Filter samples with future dates. Sometimes dates entered as 2202 instead of
  2022. I'd rather not manually correct these and guess at variations. Only
  affects a small amount of samples."
  [row]
  (and (:SampleDateTime row)
       (> 0 (LocalDateTime/.compareTo (:SampleDateTime row) (LocalDateTime/now))))) ;; all samples in system less than now (negative)

(def attributes ["StationCode"
                 "StationName"
                 "TargetLatitude"
                 "TargetLongitude"
                 ;;"Datum"
                 ;; "Program"
                 ;; "ParentProject"
                 ;; "Project"
                 "SampleTypeCode" ;; see below, many types of blanks and dups
                 "SampleDate"
                 ;;"SampleID"
                 ;;"CollectionReplicate"
                 "CollectionTime"
                 "CollectionDepth"
                 ;;"UnitCollectionDepth"
                 "MethodName"
                 "Analyte"
                 "Unit"
                 "Result"
                 ;;"MDL"
                 ;;"RL"
                 ;; wd"ResultQualCode" ;; => ("=" "<" ">" "ND" "DNQ" "NR" ">=" nil "<=" "P")
                 "DataQuality";; => ("Unknown data quality" "MetaData" "Passed" "Extensive review needed" "Some review needed" "Reject record")
                 "MatrixName" ;; => ("samplewater" "blankwater" "runoff")
                 ;;"SampleAgency"
                 ])

(defn clean-raw-file!
  "Format all dates to ISO-8601 local datetime format
  2022-06-27 14:10:00.000000 -> 2022-06-27T14:10:00"
  []
  (-> datafile
      slurp
      (str/replace #"(\d{4}-\d{2}-\d{2})\s(\d{2}:\d{2}:\d{2})(?:\.?\d*)" "$1T$2")
      (->> (spit datafile))))

(defn process-clean-file!
  "Process bulk dataset and overwrite inputfile"
  []
  (-> datafile
      (tc/dataset {:key-fn keyword
                   :column-whitelist attributes
                   :parser-fn {:SampleDate :local-date-time
                               :CollectionTime :local-date-time}})
                      ;;(tc/convert-types [:SampleDate :CollectionTime] :local-date-time)
      (tc/add-columns {:SampleDate #(map datetime/local-date-time->local-date (:SampleDate %))
                       :CollectionTime #(map datetime/local-date-time->local-time (:CollectionTime %))})
                      ;;(tc/add-column :SampleDateTime #(map str (:SampleDate %) (repeat "T") (:CollectionTime %)))
                      ;;(tc/convert-types :SampleDateTime :local-date-time)
      (tc/join-columns :SampleDateTime [:SampleDate :CollectionTime] {:separator "T"})
      (tc/convert-types :SampleDateTime :local-date-time)
      (tc/select-rows #(every? identity [(data-quality-filter %)
                                         (blank-filter %)
                                         (future-sample-filter %)
                                         (and (:Result %)
                                              (>= (:Result %) 0))
                                         (and (:TargetLatitude %)
                                              (> (:TargetLatitude %) 0))
                                         (and (:TargetLongitude %)
                                              (> 0 (:TargetLongitude %)))]))
      (ds/sort-by (juxt :SampleDateTime :Analyte :StationCode :CollectionDepth))
                      ;; when sorted with CollectionDepth, at locations where multiple depths sampled, only surface water is kept
      (ds/unique-by (juxt :SampleDateTime :Analyte :StationCode))
      (ds/drop-columns [:MatrixName :SampleTypeCode :MethodName :DataQuality :CollectionDepth])
      (tc/rename-columns {:TargetLatitude :Lat
                          :TargetLongitude :Lon})
      (tc/write! datafile)))

(defn get-processed-ds
  "Return processed dataset"
  []
  (-> datafile
      (tc/dataset {:key-fn keyword
                   :parser-fn {:SampleDateTime :local-date-time}})))

(def station-attributes [:StationCode
                         :StationName
                         :Lat
                         :Lon])

(def water-attributes [:StationCode
                       :SampleDateTime
                       :Analyte
                       :Result
                       :Unit])

(defn get-station
  "Return Station dataset (Station Table)"
  [processed-ds]
  (-> processed-ds
      (tc/select-columns station-attributes)
      (tc/unique-by station-attributes)))

(defn get-water
  "Return Water dataset (Water Table)"
  [processed-ds]
  (-> processed-ds
      (tc/select-columns water-attributes)
               ;; can't parse sql type for local-date-time, but sqlite will just store as a string
      (tc/convert-types :SampleDateTime :string)))

;;; DATABASE ;;;
(def db {:dbtype "sqlite" :dbname "./resources/SwimWild.db"})
(def ds (jdbc/get-datasource db))
(def ds-con (jdbc/get-connection ds {:auto-commit false}))

(defn init-db!
  "Initialize Tables if they don't exist"
  []
  (jdbc/execute! ds ["
    CREATE TABLE IF NOT EXISTS Station(
    StationCode TEXT NOT NULL,
    StationName TEXT,
    Lat REAL NOT NULL,
    Lon REAL NOT NULL,
    PRIMARY KEY (StationCode)
    UNIQUE (StationCode));
 "])

  (jdbc/execute! ds ["
    CREATE TABLE IF NOT EXISTS Water(
    StationCode TEXT NOT NULL,
    SampleDateTime TEXT NOT NULL,
    Analyte TEXT NOT NULL,
    Result REAL NOT NULL,
    Unit TEXT,
    PRIMARY KEY (StationCode, SampleDateTime, Analyte)
    FOREIGN KEY (StationCode) REFERENCES Station(StationCode)
    UNIQUE (StationCode, SampleDateTime, Analyte));
"])

  (jdbc/execute! ds ["
    CREATE TABLE IF NOT EXISTS Rev(
    Date TEXT NOT NULL,
    StationCount REAL NOT NULL,
    ResultCount REAL NOT NULL,
    PRIMARY KEY (Date));
"])
  (jdbc/execute! ds ["
    CREATE TABLE IF NOT EXISTS WeatherStation(
    STA TEXT NOT NULL,
    StationName TEXT NOT NULL,
    Elevation REAL NOT NULL,
    Latitude REAL NOT NULL,
    Longitude REAL NOT NULL,
    BasinName TEXT,
    HydroArea TEXT,
    PRIMARY KEY (STA));
"])
  (jdbc/execute! ds ["
    CREATE TABLE IF NOT EXISTS Precipitation(
    STATION_ID TEXT NOT NULL,
    VALUE REAL NOT NULL,
    DATA_FLAG TEXT,
    UNITS TEXT,
    DATE TEXT NOT NULL,
    PRIMARY KEY (STATION_ID, DATE)
    FOREIGN KEY (STATION_ID) REFERENCES WeatherStation(STA));
"])

  (jdbc/execute! ds ["
    PRAGMA journal_mode=WAL;
"]))

(defn create-temp-db!
  "Create temporary tables and insert temporary Water and Station data"
  []
  (let [ds (get-processed-ds)
        water (get-water ds)
        stations (get-station ds)]
    (ds-sql/create-table! ds-con water {:table-name "TmpWater"
                                        :primary-key [:StationCode :SampleDateTime :Analyte]})
    (ds-sql/create-table! ds-con stations {:table-name "TmpStation"
                                           :primary-key [:StationCode]})
    (ds-sql/insert-dataset! ds-con water {:table-name "TmpWater"})
    (ds-sql/insert-dataset! ds-con stations {:table-name "TmpStation"})))
;;
;; Old data is uploaded in a variable manner and is sometimes taken down or brought back online.
;; Just try each time to insert everything and maybe catch older data in the mix.
;; Primary key constraints will prevent most of it getting inserted.
;;

(defn update-db!
  "Try to update main tables with data from temporary tables, duplicates ignored per primary key/UNIQUE constraints"
  []
  (jdbc/execute! ds ["
    INSERT OR IGNORE INTO Station (StationCode, StationName, Lat, Lon)
    SELECT * FROM TmpStation;
    "])

  (jdbc/execute! ds ["
    INSERT OR IGNORE INTO Water (StationCode, SampleDateTime, Analyte, Result, Unit)
    SELECT * FROM TmpWater;
    "])

  (jdbc/execute! ds ["
    INSERT INTO Rev (Date, StationCount, ResultCount)
    VALUES (datetime('now'),
    (SELECT COUNT(*) FROM Station),
    (SELECT COUNT(*) FROM Water));
    "])

  (jdbc/execute! ds ["
    DROP TABLE TmpWater;
    "])

  (jdbc/execute! ds ["
    DROP TABLE TmpStation;
    "]))

(defn make-clean!
  "Delete csv file"
  []
  (io/delete-file datafile))

;; PRECIPITATION DATA UPDATES

(defn get-precip-url
  "Returns URL for CSV daily precipitation data for a station within a data range YYYY-MM-dd
     e.g. https://cdec.water.ca.gov/dynamicapp/req/CSVDataServlet?Stations=SDG&SensorNums=45&dur_code=D&Start=2020-01-01&End=2022-06-20"
  [station start end]
  (let [baseurl "https://cdec.water.ca.gov/dynamicapp/req/CSVDataServlet?Stations="
        querystring "&SensorNums=45&dur_code=D&Start="]
    (str baseurl station querystring start "&End=" end)))

(def required-cols ["STA" "Station Name" "Elevation" "Latitude" "Longitude" "Basin Name" "Hydro Area"])
(def precip-stations "resources/CDEC_Stations_daily_precip.csv")
(defn get-weather-stations
  "Get the dataset of all the weather stations"
  []
  (ds/->dataset precip-stations {:key-fn #(if (keyword? %) % (keyword (str/replace % #"\s" "")))
                                 :column-whitelist required-cols}))

(defn create-weather-station-db!
  "Insert all weather stations into dataset. Only needs to be done the first time."
  []
  (when (not (and (ds-sql/table-exists? ds-con "WeatherStation")
                  (> (-> (jdbc/execute! ds ["select count(*) as count from WeatherStation"]) first :count)
                     0)))
    (let [df (get-weather-stations)]
      (ds-sql/insert-dataset! ds-con df {:table-name "WeatherStation"}))
    (debug "WeatherStation Table created")))

(defn insert-ds-sql!
  "Create temp tables and try to update main precipitation tables, ignoring duplicates."
  [df]
  (ds-sql/drop-table-when-exists! ds-con "TmpPrecipitation")
  (ds-sql/create-table! ds-con df {:table-name "TmpPrecipitation"})
  (ds-sql/insert-dataset! ds-con df {:table-name "TmpPrecipitation"})
  (jdbc/execute! ds ["
      INSERT OR IGNORE INTO Precipitation (STATION_ID, VALUE, DATA_FLAG, UNITS, DATE)
      SELECT * FROM TmpPrecipitation;
    "])
  (debug (str "Inserted data from " ((:STATION_ID df) 0)))
  (ds-sql/drop-table-when-exists! ds-con "TmpPrecipitation"))

(defn get-precip-data!
  "Iterate through all CDEC weather stations measuring daily precipitation, get new data, and populate database"
  []
  (let [stations (-> (get-weather-stations)
                     (tc/select-columns :STA)
                     (tc/rows :as-seq) flatten)
        end (. (LocalDate/now) minusDays 1)]
    (debug (str "Gathering data from " (count stations) " stations"))
    (for [station stations]
      ;; start from either last date in db, or "2020-01-01" if no record (though it will probably remain null)
      (let [start (spy :debug (or (-> (jdbc/execute! ds ["select DATE from Precipitation where STATION_ID=? order by DATE desc limit 1;" station])
                                      (first)
                                      (:Precipitation/DATE)
                                      (LocalDate/parse)
                                      (. plusDays 1)
                                      (str))
                                  "2020-01-01"))
                                  ;; :Precipitation/DATE (first (jdbc/execute! ds ["select DATE from Precipitation where STATION_ID=? order by DATE desc limit 1;" station]))) "2020-01-01"))
            df (-> (spy :debug (:body (client/get (get-precip-url station start end) {:as :stream :debug true})))
                   (ds/->dataset {:file-type :csv
                                  :key-fn #(if (keyword? %) % (keyword (str/replace % #"\s" "_")))}))]
        (info (str station " has " (ds/row-count df) " rows for time period " start " -> " end))
        (if (and (> (ds/row-count df) 0) (not= start end))
          (-> df
              (ds/row-map (fn [row] {:DATE (str/replace (row :DATE_TIME) #"(\d{4})(\d{2})(\d{2})(?:\s\d{4})" "$1-$2-$3")}))
              (ds/drop-columns [:DATE_TIME :OBS_DATE :DURATION :SENSOR_NUMBER :SENSOR_TYPE])
              (tc/convert-types :DATA_FLAG :string)
              (insert-ds-sql!))
          (debug (str station " has no associated data")))))))

(defn get-list-missing-stations
  "Returns list of stations where no data has been gathered."
  []
  (->> (jdbc/execute! ds ["select W.STA from WeatherStation as W where W.STA not in (SELECT P.STATION_ID from Precipitation as P) or W.STA is null;"])
       (reduce (fn [acc el]
                 (conj acc (:WeatherStation/STA el))) [])))

(defn get-list-stations-with-data
  "Returns list of stations where data has been gathered."
  []
  (->> (jdbc/execute! ds ["select W.STA from WeatherStation as W where W.STA in (SELECT P.STATION_ID from Precipitation as P);"])
       (reduce (fn [acc el]
                 (conj acc (:WeatherStation/STA el))) [])))

(defn get-row-count
  [station]
  (-> (jdbc/execute! ds ["SELECT COUNT(*) AS count FROM Precipitation WHERE STATION_ID=?" station])
      first
      :count))

;; (defn remove-missing-stations-from-table!
;;   []
;;   (let [stations (get-list-missing-stations)]
;;     (for [station stations]
;;       (jdbc/execute! ds ["delete from WeatherStation where STA=?;" station]))))

;;(trace/trace-vars iwrotesomecode.dataingest/insert-ds-sql!)
;;(trace/trace-vars iwrotesomecode.dataingest/get-precip-data!)
(defn perform-update
  "ETL pipeline: GET new csv data, process it, update db, clean up db and folder"
  []
  (when (not= 404 (:status (client/head link {:throw-exceptions false})))
    (info (str "Downloading file: " file " \n \t Saving as: water-" today ".csv"))
    (try (get-file!)
         (info "Cleaning file")
         (clean-raw-file!)
         (info "Processing file")
         (process-clean-file!)
         (info "Initializing database")
         (init-db!)
         (info "Creating temporary tables")
         (create-temp-db!)
         (info "Updating db")
         (update-db!)
         (info (str "Deleting old csv file: " datafile))
         (make-clean!)
         (info "Water Quality updates complete!")
         (catch Exception e
           (error "Exception! " e)))
    (info "Getting weather station data")
    (create-weather-station-db!)
    (info "Getting precipitation data")
    ;; Seems to be more server issues in the daytime. Run cron at night for dailies.
    (try (get-precip-data!)
         (catch Exception e
           (error "Exception! " e)))
    (info "ALL UPDATES COMPLETE!")))

(defn -main
  [& args]
  (perform-update))

;;;;;;;;;;;;
;;;;;;;;;;;; Some comments/notes
;;;;;;;;;;;;
(comment

  (defn missing-collection-time [row]
    (and (:CollectionTime row)
         (= (str->local-time "00:00:00") (:CollectionTime row))))

  (defn str->local-date-time
    "Parse ISO-8601 local-date-time yyyy-MM-dd T HH:mm:ss"
    [str]
    (->> (DateTimeFormatter/ISO_LOCAL_DATE_TIME)
         (LocalDateTime/parse str)))

  (defn str->local-date
    "Parse ISO-8601 local-date yyyy-MM-dd"
    [str]
    (->> (DateTimeFormatter/ISO_LOCAL_DATE)
         (LocalDate/parse str)))

  (defn str->local-time
    "Parse ISO-8601 local-time HH:mm:ss"
    [str]
    (->> (DateTimeFormatter/ISO_LOCAL_TIME)
         (LocalTime/parse str)))

  ;; "client/head is returning 403, but this is too inefficient to download everything first."
  (-> (:headers (client/get link))
      (get "Content-Length")
      (Integer/parseInt)
      (/ (* 1024 1024))
      (str " MB")))
;;https://cdec.water.ca.gov/dynamicapp/req/CSVDataServlet?Stations=SDG&SensorNums=45&dur_code=D&Start=2020-01-01&End=2022-06-20
;;https://cdec.water.ca.gov/dynamicapp/wsSensorData
(comment
  (def deduped-data (-> (ds/sort-by swim-cleaned (juxt :SampleDateTime :Analyte :StationCode :CollectionDepth))
                       ;; when sorted with CollectionDepth, at locations where multiple depths sampled, only surface water is kept
                        (ds/unique-by (juxt :SampleDateTime :Analyte :StationCode))
                        (ds/drop-columns [:MatrixName :MethodName :DataQuality :CollectionDepth])))

  (->> (ds/group-by swim-cleaned (juxt :SampleDateTime :Analyte :StationCode))
       vals
       (filter #(> (ds/row-count %) 1))
       (apply ds/concat))

  (->> (ds/group-by swim-cleaned (juxt :SampleDateTime :Analyte :StationCode))
       (vals)
       (filter #(> (ds/row-count %) 1))
       (apply (fn [x] (ds/sort-by x :CollectionDepth :tech.numerics/<))))

  (-> (ds/sort-by s (juxt :SampleDateTime :Analyte :StationCode :CollectionDepth))
      (ds/unique-by (juxt :SampleDateTime :Analyte :StationCode))

      (->> (ds/group-by swim-cleaned (juxt :SampleDateTime :Analyte :StationCode))
           (vals)
           (filter #(> (ds/row-count %) 1))
           (apply (fn [x] (ds/sort-by x {:key-fn :CollectionDepth
                                         :compare-fn clojure.core/<}))))))

(comment
  (ds/descriptive-stats swim-cleaned)
  (ds/brief swim-cleaned)
  ;; OCEAN#18_SL)
  (tc/info swim :columns)
  (-> swim
      (tc/unique-by ,,, :SampleTypeCode)
      (tc/select-columns ,,, :SampleTypeCode)
      (tc/rows :as-seq)
      flatten)
  ;; => ("Grab"
  ;; "FieldBlank"
  ;; "FieldBLDup_Grab"
  ;; "LabBlank"
  ;; "LCS"
  ;; "Not Recorded"
  ;; "CHK"
  ;; "Integrated"
  ;; "EquipBlank"
  ;; "FilterBlank"
  ;; "NEC"
  ;; "FieldBLBlank"
  ;; "FieldBLDup_Split"
  ;; "TravelBlank")
  (-> swim
      (tc/unique-by ,,, :DataQuality)
      (tc/select-columns ,,, :DataQuality)
      (tc/rows :as-seq)
      flatten)
  ;; => ("Unknown data quality"
  ;; "MetaData" ;; Corresponds to Field Blanks and Dups.
  ;; "Passed"
  ;; "Extensive review needed"
  ;; "Some review needed"
  ;; "Reject record")

  (-> swim
      (tc/unique-by ,,, :MatrixName)
      (tc/select-columns ,,, :MatrixName)
      (tc/rows :as-seq)
      flatten)
  ;; => ("samplewater" "blankwater" "runoff") ;;blankwater should be rejected with MetaData
  )
