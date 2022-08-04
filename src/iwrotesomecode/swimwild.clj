(ns iwrotesomecode.swimwild
  (:require [org.httpkit.server :refer [run-server]]
            [reitit.ring :as ring]
            [reitit.ring.middleware.exception :refer [exception-middleware]]
            [reitit.ring.middleware.muuntaja :refer [format-negotiate-middleware
                                                     format-request-middleware
                                                     format-response-middleware]]
            [muuntaja.core :as m]
            [clojure.java.shell :as shell])
  (:gen-class))

(defonce server (atom nil))

(def app
  (ring/ring-handler
   (ring/router
    [["/api" {:get (fn [req]
                     {:status 200
                      :body {:hello "world"}})}]]
    {:data {:muuntaja m/instance
            :middleware [format-negotiate-middleware
                         format-response-middleware
                         exception-middleware
                         format-request-middleware]}})
   (ring/routes
    (ring/redirect-trailing-slash-handler)
    (ring/create-default-handler {:not-found (constantly {:status 404
                                                          :body "Route not found."})}))))
(defn run-shell-cmd
  "Run a command represented as a string in an OS shell (default=/bin/bash).
  Example: 'ls -ldF *'  "
  [cmd-str]
  (let [result (shell/sh *os-shell* "-c" cmd-str)]
    (if (= 0 (t/safe-> :exit result))
      result
      (throw (RuntimeException.
              (str "shell-cmd: clojure.java.shell/sh failed. \n"
                   "cmd-str:"     cmd-str        "\n"
                   "exit status:" (:exit result) "\n"
                   "stderr:"      (:err  result) "\n"
                   "result:"      (:out  result) "\n"))))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Server started")
  (reset! server (run-server app {:port 4004})))

(defn stop-server []
  (when-not (nil? @server)
    (@server :timeout 100)
    (reset! server nil)))

(defn restart-server []
  (stop-server)
  (-main))

(comment
  (restart-server)
  @server
  (-main)
  (stop-server)
  (app {:request-method :get
        :uri "/api/"}))
