(ns blog.core
  (:require [blog.commands :refer [dispatch-command]])
  (:gen-class :main :true))

(defn -main [& args]
  (dispatch-command args))
