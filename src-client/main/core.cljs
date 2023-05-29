(ns main.core
  (:require
   ["d3" :as d3]
   ["leaflet" :as L]
   ["leaflet.markercluster"]
   ["react-dom/client" :as rdom]
   ["react-leaflet" :refer [MapContainer Marker Popup TileLayer]]
   ["@changey/react-leaflet-markercluster$default" :as MarkerClusterGroup] ;; https://github.com/yuzhva/react-leaflet-markercluster/pull/189
   [clojure.string :as str]
   [helix.core :refer [$ defnc]]
   [helix.dom :as d]
   [helix.hooks :as hooks]))

;; (defnc app []
;;   ($ MapContainer {:center #js [51.505 -0.09]
;;                    :zoom 13
;;                    :scrollWheelZoom false}
;;      ($ TileLayer {:attribution "\u00A9 <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors"
;;                    :url "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"})
;;      ($ Marker {:position #js [51.505 -0.09]}
;;         ($ Popup (d/p "A pretty CSS3 popup."
;;                       (d/br)
;;                       "Easily customizable.")))))
;;`<,`>s/\([a-zA-Z]*\):/:\1/g
;

;;(defonce appstate (atom {}))
(defn set-local-storage!
  "set 'key' in local storage to data -- assuming data is a JSON response from api call"
  [key data]
  (-> (.-localStorage js/window)
      (.setItem key (js/JSON.stringify data))))

(defn get-local-storage
  "get value of 'key' from localStorage"
  [key]
  (-> (.-localStorage js/window)
      (.getItem key)
      (js/JSON.parse)
      (js->clj :keywordize-keys true)))

(defn remove-local-storage!
  "removes value of 'key' from localStorage"
  [key]
  (-> (.-localStorage js/window)
      (.removeItem key)))

(defn clj->json
  "convert clojure map to JSON"
  [m]
  (->> (clj->js m)
       (js/JSON.stringify)))
;; (defn handler [state key response]
;;   (swap! state assoc key (js->clj response :keywordize-keys true)))

(defn error-handler [err]
  (.log js/console (str "Error: " (.-message err))))

;; (defn str->date
;;   "1990-05-01 13:00:00 to #inst 1990-05-01T13:00:00"
;;   [datestr]
;;   (let [dt (str/replace datestr #"\s" "T")]
;;     #inst dt))

(defn fetch-stations
  "fetch and store stations in localStorage"
  [url]
  (-> (js/fetch url)
      (.then #(.json %))
      (.then #(->> (js->clj % :keywordize-keys true)
                   (assoc {} :fetched true :stations)))
      (.then #())
      (.then #(clj->js %))
      (.then #(set-local-storage! "stations" %))
      ;; (.then #((juxt (partial handler state :stations)
      ;;                (partial set-local-storage! "stations")) %))
      (.catch #(error-handler %))))

;; (defn get-content
;;   [mark]
;;   (p/let [station-code (:StationCode mark)
;;           url (str "https://swim.josephdumont.com/api/" (js/encodeURIComponent station-code) "?pagesize=9")
;;           data (-> (js/fetch url)
;;                    (.then #(.json %))
;;                    (js->clj :keywordize-keys true)
;;                    (.catch #(error-handler %)))
;;           popup-string (str (:StationName mark) " (" (:StationCode mark) ")")
;;           el (d/createDom "div" nil
;;                           (d/createDom "b" #js {:textContent popup-string}))
;;           col-keys [:SampleDateTime :Analyte :Result]
;;           tabdata (mapv #(select-keys % col-keys) data)
;;           row-header (js/Array.
;;                       (d/createDom "tr" nil
;;                                    (-> (for [th (mapv name col-keys)]
;;                                          (d/createDom "th" #js {:textContent th}))
;;                                        (js/Array.from))))
;;           body (-> (for [row tabdata]
;;                      (d/createDom "tr" nil
;;                                   (-> (for [col (vals row)]
;;                                         (d/createDom "td" #js {:textContent col}))
;;                                       (js/Array.from))))
;;                    (js/Array.from))
;;           table (d/createDom "table" nil (.concat row-header body))
;;           html (d/append el table)]
;;     (prn data)
;;     (prn popup-string)
;;     (prn el)
;;     (prn (type el))
;;     el))

;; ;;https://stackoverflow.com/questions/59306768/marker-clustering-leaflet-markercluster-with-react-leaflet-2-0
;; ;;https://codesandbox.io/s/marker-clustering-leafletmarkercluster-with-react-leaflet-20-kvxxr?file=/src/MarkerCluster.jsx
;; (def mcg (.markerClusterGroup L #js {:maxClusterRadius 30}))
;; (defnc Marker-cluster [{:keys [markers]}]
;;   (let [thismap (useMap)
;;         icon-size #js [15 15]
;;         popup-anchor #js [0 0]
;;         map-icon (.divIcon L #js {:iconSize icon-size
;;                                   :popupAnchor popup-anchor})]
;;     (hooks/use-effect
;;       [thismap markers]
;;       (.clearLayers mcg)
;;       (dorun (->> markers
;;                   (map (fn [mark]
;;                          (let [lat (:Lat mark)
;;                                lon (:Lon mark)
;;                                marker (.marker L #js [lat lon] #js {:icon map-icon
;;                                                                     :data mark
;;                                                                     :key (:StationCode mark)})
;;                                ;;popup-content-ok (goog.dom/createDom "p" #js {:textContent (str (:StationName mark) " (" (:StationCode mark) ")")})
;;                                ;;popup-content-err (d/p (str (:StationName mark) " (" (:StationCode mark) ")"))]
;;                                popup-content #(get-content mark)]
;;                            (-> marker
;;                                (.addTo mcg)
;;                                ;; this doesn't work. I'm passing a function that returns HTMLElement
;;                                ;; but it throws an error. Instead, do a more react way, and just
;;                                ;; set an active marker, then display a popup for which marker is active onclick.
;;                                (.bindPopup popup-content)))))))
;;       (.addLayer thismap mcg))))

;; https://www.robinwieruch.de/react-hooks-fetch-data/
(defn get-content
  [data]
  (let [entry (first data)
        station-name (:StationName entry)
        station-code (:StationCode entry)
        col-keys [:Analyte :Result :SampleDateTime]
        limit 5
        tabdata (->> (mapv #(select-keys % col-keys) data)
                     (take limit))]
    (d/div {:class "station-popup"
            :style {:width "500"}}
           (d/b (str station-name " (" station-code ")"))
           (d/table {:class "data"}
                    (d/thead
                     (d/tr
                      (d/th "Date")
                      (d/th "Analyte")
                      (d/th {:style {:white-space "pre"}} "Result\n(cfu/100mL)")
              ;; (for [col col-keys]
              ;;   (d/th {:key col} (name col)))
                      ))
                    (d/tbody
                     (for [row tabdata]
                       (d/tr {:key (str (juxt :SampleDateTime :Analyte) row)}
                             (d/td ((comp first #(str/split % #" ") :SampleDateTime) row))
                             (d/td (:Analyte row))
                             (d/td {:style {:text-align "right"}} (:Result row)))))))))
(defn use-fetch-data
  [url]
  (let [[data set-data] (hooks/use-state nil)]
    (hooks/use-effect
     :auto-deps
     (let [ignore? (atom false)]
       (-> (js/fetch url)
           (.then #(.json %))
           ;;(.then #(set-local-storage! "data" %))
           (.then #(set-data %))
           (.catch #(error-handler %)))))))

;; https://rollacaster.github.io/hiccup-d3/
(defnc d3-component
  [{:keys [data]}]
  (let [svgref (hooks/use-ref nil)]
    (hooks/use-effect
     [data]
     (let [;; _ (-> (d3/select @svgref)
           ;;       (.transition)
           ;;       (.remove))
           margin {:top 40 :right 40 :bottom 40 :left 40}
           width (- 500 (:left margin) (:right margin))
           height (- 300 (:top margin) (:bottom margin))
           parse-time (d3/utcParse "%Y-%m-%d %H:%M:%S")
           get-analyte (fn [analyte] (filter #(= analyte (:Analyte %)) data))
           get-dates #(->> (map :SampleDateTime %)
                           (map parse-time))
           ent (get-analyte "Enterococcus")
           ent-values (map :Result ent)
           ent-dates (get-dates ent)
           ;; fec (get-analyte "Coliform, Fecal")
           ;; fec-values (map :Result fec)
           ;; fec-dates (get-dates ent)
           ;; col (get-analyte "Coliform, Total")
           ;; col-values (map :Result col)
           ;; col-dates (get-dates col)
           maxval (apply max ent-values)
           mindate (apply min ent-dates)
           maxdate (apply max ent-dates)
           ;; maxval (max (apply max ent-values)
           ;;             (apply max fec-values)
           ;;             (apply max col-values))
           ;; mindate (min (apply min ent-dates)
           ;;              (apply min fec-dates)
           ;;              (apply min col-dates))
           ;; maxdate (max (apply max ent-dates)
           ;;              (apply max fec-dates)
           ;;              (apply max col-dates))
           x (-> (d3/scaleUtc)
                 (.domain #js [mindate maxdate])
                 (.range #js [0 width]))
           y (-> (d3/scaleLinear)
                 (.domain #js [0 maxval])
                 (.range #js [height 0]))
           make-line (fn [analyte]
                       (-> (d3/line)
                           (.x (fn [d] (x (when #(= analyte (:Analyte d))
                                            (-> (:SampleDateTime d) parse-time)))))
                           (.y (fn [d] (y (when #(= analyte (:Analyte d))
                                            (-> (:Result d))))))))
           ent-line (make-line "Enterococcus")
           ;; fec-line (make-line "Coliform, Fecal")
           ;; col-line (make-line "Coliform, Total")
           svg (-> (d3/select @svgref)
                   (.append "svg")
                   (.attr "width" (+ width (:left margin) (:right margin)))
                   (.attr "height" (+ height (:top margin) (:bottom margin)))
                   (.append "g")
                   (.attr "transform" (str "translate("  (:left margin) ", " (:right margin) ")")))
           third #(first (next (next %)))]
       (-> svg
           (.append "g")
           (.attr "transform" (str "translate(0," height ")"))
           (.call (d3/axisBottom x)))

       (-> svg
           (.append "g")
           (.call (d3/axisLeft y)))

       (-> svg
           (.append "path")
           (.data [data])
           (.attr "d" ent-line)
           (.style "stroke" (first d3/schemeTableau10))
           (.style "stroke-width" 1.5)
           (.style "fill" "transparent"))
       ;; (-> svg
       ;;     (.append "path")
       ;;     (.data [data])
       ;;     (.attr "d" fec-line)
       ;;     (.style "stroke" (second d3/schemeTableau10))
       ;;     (.style "stroke-width" 1.5)
       ;;     (.style "fill" "transparent"))
       ;; (-> svg
       ;;     (.append "path")
       ;;     (.data [data])
       ;;     (.attr "d" col-line)
       ;;     (.style "stroke" (third d3/schemeTableau10))
       ;;     (.style "stroke-width" 1.5)
       ;;     (.style "fill" "transparent"))
       ))

    (d/svg {:height 300
            :width 500
            :ref svgref})))
  ;; (let [size 300
  ;;       parse-time (d3/utcParse "%Y-%m-%d %H:%M:%S")
  ;;       dates (->> (map :SampleDateTime data)
  ;;                  (map parse-time))
  ;;       values (map :Result data)
  ;;       x (-> (d3/scaleUtc)
  ;;             (.domain (into-array [(apply min dates) (apply max dates)]))
  ;;             (.range (into-array [0 size])))
  ;;       y (-> (d3/scaleLinear)
  ;;             (.domain (into-array [0 (apply max values)]))
  ;;             (.range (into-array [size 0])))
  ;;       line (-> (d3/line)
  ;;                (.x (fn [d] (x (-> (:SampleDateTime d) parse-time))))
  ;;                (.y (fn [d] (y (:Result d)))))]
  ;;   (d/svg {:viewBox (str 0 " " 0 " " size " " size)}
  ;;          (d/path {:d (line data)
  ;;                   :fill "transparent"
  ;;                   :stroke (first d3/schemeCategory10)}))))

  ;; (let [d3-container (hooks/use-ref nil)]
  ;;   (hooks/use-effect
  ;;    :auto-deps
  ;;    (when (and data @d3-container)
  ;; (let [d3-container (hooks/use-ref nil)
  ;;       margin {:top 10 :right 10 :bottom 10 :left 10}
  ;;       width (- 600 (:left margin) (:right margin))
  ;;       height (- 400 (:top margin) (:bottom margin))
  ;;       parse-time (d3/utcParse "%Y-%m-%d %H:%M:%S")
  ;;       dates (->> (map :SampleDateTime data) ;;(map #(js/Date.parse %))
  ;;                  (map parse-time))
  ;;       _ (prn dates)
  ;;       values (map :Result data)
  ;;       x (-> (d3/scaleUtc)
  ;;             (.domain #js [(apply min dates) (apply max dates)])
  ;;             (.range #js [0 width]))
  ;;       y (-> (d3/scaleLinear)
  ;;             (.domain #js [0 (apply max values)])
  ;;             (.range #js [height 0]))
  ;;       line (-> (d3/line)
  ;;                (.x (fn [d] (x (->> (:SampleDateTime d) ;;(map #(js/Date.parse %))
  ;;                                    (parse-time)))))
  ;;                (.y (fn [d] (y (:Result d)))))
  ;;       svg (-> (d3/select @d3-container)
  ;;               (.append "svg")
  ;;               (.attr "width" (+ width (:left margin) (:right margin)))
  ;;               (.attr "height" (+ height (:top margin) (:bottom margin)))
  ;;               (.append "g")
  ;;               (.attr "transform" (str "translate("  (:left margin) ", " (:right margin) ")")))]

  ;;   (-> svg
  ;;       (.append "g")
  ;;       (.attr "transform" (str "translate(0," height ")"))
  ;;       (.call (d3/axisBottom x)))

  ;;   (-> svg
  ;;       (.append "g")
  ;;       (.call (d3/axisLeft y)))

  ;;   (-> svg
  ;;       (.append "path")
  ;;       (.datum (clj->js data))
  ;;       (.attr "d" line)
  ;;       ;;(.attr "d" (line data))
  ;;       (.style "stroke" (first d3/schemeTableau10))
  ;;       (.style "stroke-wdith" 1.5)
  ;;       (.style "fill" "none"))
  ;;   (d/svg {:class "d3-component"
  ;;           :ref d3-container
  ;;           :width width
  ;;           :height height}
  ;;          svg))
  ;;)

  ;; https://leaflet-extras.github.io/leaflet-providers/preview/
(defnc app []
  (let [[records set-records] (hooks/use-state (or (get-local-storage "stations")
                                                   {:stations [] :fetched false}))
        [active-station set-active-station] (hooks/use-state nil)
        selected-ref (hooks/use-ref nil)
        [active-data set-active-data] (hooks/use-state nil)
        [url set-url] (hooks/use-state nil)
        [loading? set-loading] (hooks/use-state false)
        [error? set-error] (hooks/use-state false)
        api "https://swim.josephdumont.com/api/"]

    (hooks/use-effect
     :once
     (when (= false (:fetched records))
       (-> (fetch-stations "https://swim.josephdumont.com/api/stations")
           (.then #(set-records (get-local-storage "stations"))))))

    (hooks/use-effect
     [url]
     (when (some? url)
       (let [ignore? (atom false)]
         (set-error false)
         (set-loading true)
         (-> (js/fetch url)
             (.then #(.json %))
             (.then #(js->clj % :keywordize-keys true))
             (.then #(when (not @ignore?)
                       (set-active-data %)))
             (.catch #(do (error-handler %)
                          (set-error true))))
         (set-loading false)
         #(do (reset! ignore? true)))))

    ($ MapContainer {:center #js [32.7 -117.1]
                     :zoom 11
                     :scrolWheelZoom false
                     :closeOnClick true
                     :closePopupOnClick true
                     :tap false}
       ($ TileLayer {:url "https://stamen-tiles-{s}.a.ssl.fastly.net/toner/{z}/{x}/{y}{r}.{ext}"
                     :attribution "Map tiles by <a href=\"http://stamen.com\">Stamen Design</a>, <a href=\"http://creativecommons.org/licenses/by/3.0\">CC BY 3.0</a> \u2014 Map data \u00A9 <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors"
                     :subdomains "abcd"
                     :minZoom 0
                     :maxZoom 20
                     :ext "png"})
       ($ MarkerClusterGroup {:maxClusterRadius 30}
          (->> (:stations records)
               (map (fn [mark]
                        ;; get hue-rotation style here
                      ($ Marker {:class (if (= mark active-station) "active-station" "")
                                 :key {:StationCode mark}
                                 :position #js [(:Lat mark) (:Lon mark)]
                                 :eventHandlers #js {:click
                                                     #(do
                                                        (set-active-station mark)
                                                        (set-url (str api (js/encodeURIComponent (:StationCode mark)))))} ;;"?pagesize=9"
                                 :icon (.divIcon L #js {:iconSize #js [15 15]
                                                        :popupAnchor #js [0 0]})})))))
       (when (some? active-data)
         (prn active-data)
         (d/div {:class "d3-popup"}
                ($ Popup
                   {:autoClose true
                    :closeOnClick true
                    :width "auto !important"
                    :height "auto !important"
                    :maxHeight "100%"
                    :maxWidth "100%"
                    :position #js [(:Lat active-station) (:Lon active-station)]
                    :eventHandlers #js {:close #(do (set-active-station nil)
                                                    (set-url nil)
                                                    (set-active-data nil))}}
                   (cond
                     error? (d/div "Uh-oh, the data is gone. Wait 72 hours after rain to swim.")
                     loading? (d/div "Loading...")
                     :else (d/div
                            (get-content active-data)
                            ($ d3-component {:data active-data})))))))))

(defn ^:export main []
  (let [root (rdom/createRoot (js/document.getElementById "app"))]
    (.render root ($ app))))
