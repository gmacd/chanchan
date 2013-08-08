(ns blog.server
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.response :refer [response]]
            [ring.middleware.file :refer [wrap-file]]))
;            [blog.pipeline :refer [build-site asset-types]]))

; TODO Remove ring dependencies

; TODO better fallback - 404?  Is this really an error handler?
;(defn handler [request]
;  (response "hello world"))

;(def app
;  (wrap-file handler "site/"))

(defn launch-server [])
;  (run-jetty app {:port 3000}))
