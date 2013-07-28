(ns blog.core
  (:require [watchtower.core :refer [watcher on-change file-filter rate ignore-dotfiles extensions]]
            [blog.pipeline :refer [build-site asset-types]]
            [blog.server :refer [launch-server]]))

; TODO Move watch related code to seperate file?

(defn- on-posts-changed [post-files]
  "Rebuild site when files changed"
  ; TODO - Currently rebuilds entire site - worth making smarter?  Right now, no!
  ; TODO check file stamp for files newer than start time
  (build-site))

; File watcher future for .md posts
; TODO watch more than just posts
(def post-watcher (watcher (:src-path (:post asset-types))
                           (rate 50)
                           (file-filter ignore-dotfiles)
                           (file-filter (extensions :md))
                           (on-change on-posts-changed)))

(defn -main [& args]
  (build-site)
  (launch-server))
