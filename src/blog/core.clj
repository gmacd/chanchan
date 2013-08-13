(ns blog.core
  (:require [blog.commands :refer [dispatch-command]])
  (:gen-class))

(defn -main [& args]
  (dispatch-command args))
