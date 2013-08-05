(ns blog.commands
  (:require [clojure.java.io :as jio]
            [clostache.parser :refer [render]]
            [blog.pipeline :refer [build-site asset-types]]
            [blog.server :refer [launch-server]])
  (:import (java.text SimpleDateFormat ParsePosition)
           (java.util Date)))


; TODO Commands: create - create blog with default templates and folders
;                post - new post
;                page - new page
;                server - start server
;                clean - remove all generated files (pages, posts, index.html)
;                help - (default)

(def ^:const start-dir (System/getProperty "user.dir"))

; TODO confirm overwrites
; TODO config file with blog title + other things?  If so, remove hard-coded
;      title from index template.
(defn create [args]
  (println " Creating new blog...")
  (doseq [path (map #(str start-dir "/" %) [(-> asset-types :page :dest-path)
                                            (-> asset-types :post :dest-path)
                                            (-> asset-types :page :src-path)
                                            (-> asset-types :post :src-path)])]
    (.mkdirs (jio/file path))
    (println " Created folder: " path))
      
  ; Copy index
  (jio/copy (jio/file (jio/resource "pages/index.md"))
            (jio/file (str start-dir "/" (:dest-path (:page asset-types)) "/index.md"))))

(defn post [args]
  ; TODO Convert title into filesystem-safe title
  (let [title (if (empty? args) "New-Post" (first args))
        date-str (-> (SimpleDateFormat. "yyyy-MM-dd") (.format (Date.)))
        filename (str date-str "-" (.toLowerCase title) ".md")
        post-template (slurp (jio/resource "templates/post.md"))
        new-post (render post-template
                         {:title title
                          :date date-str})]
    (spit (str start-dir "/" (-> asset-types :post :src-path) "/" filename) new-post)))

(defn page [args]
  (println "new page: " args))

; TODO Port override
(defn server [args]
  (build-site start-dir)
  (launch-server))

(def commands
  {:create [create "Create blog at given (existing) path.\n E.g. 'chanchan create mynewblog'"]
   :post [post "Create a new post with todays data and the given filename title.\n E.g. 'chanchan post my-next-post' will create a new post with the filename composed of the current date and given title."]
   :page [page "Create a new page with the given filename title.\n E.g. 'chanchan page my-new-page' will create a new page with a filename of the given title."]
   :server [server "Launch the server at the specified port. If unspecified, use port 3000."]})

; TODO help if command not found
(defn dispatch-command [args]
  (if (empty? args)
    ; TODO print out help
    (println "No args")
    ; Handle args
    (let [[command-name & command-args] args
          command ((keyword command-name) commands)
          commandfn (first command)]
      (if (nil? commandfn)
        ; TODO print out help
        (println "Unknown command")
        (commandfn command-args)))))
