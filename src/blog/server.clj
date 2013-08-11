(ns blog.server
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.response :refer [response]]
            [ring.middleware.file :refer [wrap-file]]))

; TODO Remove ring dependencies

; TODO better fallback - 404?  Is this really an error handler?
(defn handler [request]
  (response "hello world"))

(defn launch-server []
  (let [app (wrap-file handler "site/")]
    (run-jetty app {:port 3000})))
