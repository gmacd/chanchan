(ns blog.core
  (:require [clojure.java.io :as jio]
            [watchtower.core :refer [watcher on-change file-filter rate ignore-dotfiles extensions]]
            [blog.pipeline :refer [build-site asset-types]]
            [blog.server :refer [launch-server]])
  (:gen-class))

; TODO Move watch related code to seperate file?
; TODO Support disqus comments
; TODO Commands: create - create blog with default templates and folders
;                post - new post
;                page - new page
;                server - start server
;                clean - remove all generated files (pages, posts, index.html)
;                help - (default)

(defn start-dir []
  "Return the app start dir"
  (System/getProperty "user.dir"))


; TODO confirm overwrites
; TODO config file with blog title + other things?  If so, remove hard-coded
;      title from index template.
(defn create [args]
  (println " Creating new blog files...")
  (let [post-path (str (start-dir) "/" (:dest-path (:post asset-types)))
        page-path (str (start-dir) "/" (:dest-path (:page asset-types)))]
    
    ; Create folders
    (.mkdirs (jio/file post-path))
    (.mkdirs (jio/file page-path))
    (println " Created folder: " post-path)
    (println " Created folder: " page-path)

    ; Copy index
    (jio/copy (jio/file (jio/resource "pages/index.md"))
              (jio/file (str (pwd) "/" (:dest-path (:page asset-types)) "/index.md")))))

(defn post [args]
  (println "new post: " args))

(defn page [args]
  (println "new page: " args))

; TODO Port override
(defn server [args]
  (build-site (start-dir))
  (launch-server))

(def commands {:create [create "Create blog at given (existing) path.\n E.g. 'chanchan create mynewblog'"]
               :post [post "Create a new post with todays data and the given filename title.\n E.g. 'chanchan post my-next-post' will create a new post with the filename composed of the current date and given title."]
               :page [page "Create a new page with the given filename title.\n E.g. 'chanchan page my-new-page' will create a new page with a filename of the given title."]
               :server [server "Launch the server at the specified port. If unspecified, use port 3000."]})

; TODO help if command not found
(defn dispatch-command [command-args]
  (let [[command-name & args] command-args
        command ((keyword command-name) commands)
        commandfn (first command)]
    (commandfn args)))

; TODO - Currently rebuilds entire site - worth making smarter?  Right now, no!
; TODO check file stamp for files newer than start time
;(defn- on-posts-changed [post-files]
;  "Rebuild site when files changed"
;  (build-site))

; File watcher future for .md posts
; TODO watch more than just posts
; TODO re-enable as part of server
;(def post-watcher (watcher (:src-path (:post asset-types))
;                           (rate 50)
;                           (file-filter ignore-dotfiles)
;                           (file-filter (extensions :md))
;                           (on-change on-posts-changed)))

(defn -main [& args]
  (dispatch-command args))
