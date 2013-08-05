(ns blog.core
  (:require [watchtower.core :refer [watcher on-change file-filter rate ignore-dotfiles extensions]]
            [blog.commands :refer [dispatch-command]])
  (:gen-class))

; TODO Move watch related code to seperate file?
; TODO Support disqus comments

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
