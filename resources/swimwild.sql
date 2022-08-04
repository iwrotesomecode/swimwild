-- :name create-table-stations
-- :command :execute
-- :result :raw
-- :doc Creates Water Quality Stations Table
CREATE TABLE IF NOT EXISTS stations (
  station_code TEXT PRIMARY KEY,
  station_name TEXT,
  geom geometry(POINT, 4326));

-- :name create-table-water
-- :command :execute
-- :result :raw
-- :doc Creates Water Quality Results Table
CREATE TABLE IF NOT EXISTS water (
  station_code TEXT NOT NULL,
  sample_datetime TIMESTAMP NOT NULL,
  analyte TEXT NOT NULL,
  result REAL NOT NULL,
  unit TEXT,
  PRIMARY KEY (station_code, sample_datetime, analyte),
  FOREIGN KEY (station_code) REFERENCES stations(station_code)
  );

-- :name create-table-rev
-- :command :execute
-- :result :raw
-- :doc Creates Revision Table
CREATE TABLE IF NOT EXISTS rev (
  date TIMESTAMP PRIMARY KEY,
  station_count INTEGER,
  result_count INTEGER,
  weather_station_count INTEGER,
  precip_count INTEGER
  );

-- :name create-table-weather
-- :command :execute
-- :result :raw
-- :doc Creates Table of Weather Stations
CREATE TABLE IF NOT EXISTS weather_station (
  station_id TEXT PRIMARY KEY,
  station_name TEXT,
  geom geometry(POINTZ, 4326),
  basin_name TEXT,
  hydro_area TEXT
  );

-- :name create-table-precipitation
-- :command :execute
-- :result :raw
-- :doc Creates Table of Precipitation Results
CREATE TABLE IF NOT EXISTS precipitation (
  station_id TEXT NOT NULL,
  value REAL,
  data_flag TEXT,
  date TIMESTAMP,
  PRIMARY KEY (station_id, date),
  FOREIGN KEY (station_id) REFERENCES weather_station(station_id)
  );


-- :name get-station-results :? :*
-- :command :query
-- :result :raw
-- :doc Query water quality results by station id (:id), with pagesize (:ps) and page offset (:off)
SELECT * FROM water
JOIN station ON water.station_code=stations.station_code
WHERE station.station_code=:id
ORDER BY sample_dateteim DESC
LIMIT :ps OFFSET :off


-- :name station-search :? :*
-- :command :query
-- :result :raw
-- :doc Search for station by name
SELECT DISTINCT stations.station_code, station_name
FROM water JOIN stations ON water.station_code=stations.station_code
WHERE stations.station_code LIKE ('%' || :id || '%')
ORDER BY sample_datetime DESC
LIMIT :ps OFFSET :off

-- :name get-all-stations :? :*
-- :command :query
-- :result :raw
-- :doc Get all station coordinates
SELECT station_code, station_name, geom
FROM stations
