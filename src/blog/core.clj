(ns blog.core
  (:require [clojure.java.io :as jio])
  (:use [markdown.core :only (md-to-html-string)]
        [hiccup core page]
        clostache.parser
        watchtower.core
        ring.adapter.jetty
        ring.util.response
        [ring.middleware resource file file-info]))

(defn html-post [title body]
  (html5 [:head
          [:link {:rel "stylesheet" :type "text/css" :href "/bootstrap/css/bootstrap.css"}]
          [:title title]]
         [:body body]))

(defn files-with-extension [src ext]
  "Return all files in src with the extension ext.  E.g. '.md'"
  (->> (jio/file src)
       (file-seq)
       (filter #(.endsWith (.getName %) ext))))

(defn with-ext [path ext]
  "Return a new file with the extension added or replaced with 'ext'"
  (let [period-idx (.lastIndexOf path ".")]
    (if (not= period-idx -1)
      (str (.substring path 0 period-idx) "." ext)
      (str path "." ext))))

(defn with-path [src-path dest-folder-path]
  "Convert src path to dest path"
  (str dest-folder-path "/" (.getName src-path) "html"))

(defn convert-md-file [src dest]
  (let [md-contents (slurp src)]
    (->> md-contents
         (md-to-html-string)
         (html-post "post?")
         (spit dest))))

(defn convert-all-md-files [src-path dest-path]
  (let [md-files (files-with-extension src-path ".md")]
    (doseq [md-file md-files]
      (convert-md-file md-file
                       (with-ext (with-path md-file dest-path) "html")))))

(defn generate-homepage [src-path dest-path]
  (spit (str dest-path "/index.html")
        (-> (slurp (str src-path "/index.html"))
            (render {:title "wot?"
                     :body "eh?"})))
  (println "Converted homepage"))
    

; TODO better fallback
(defn handler [request]
  (response "hello world"))

(def app
  (wrap-file handler "site/"))


(def src-posts-path "assets/posts")
(def dest-posts-path "site/posts")
(def templates-path "assets/templates")
(def dest-root-path "site")


(defn- on-posts-changed [post-files]
  "Called when a post has been modified"
  (doseq [md-file post-files]
    (convert-md-file md-file
                     (with-ext (with-path md-file dest-posts-path) "html"))
    (println (str "Converted file: " md-file))))


; File watcher future for .md posts
(def post-watcher (watcher src-posts-path
                           (rate 50)
                           (file-filter ignore-dotfiles)
                           (file-filter (extensions :md))
                           (on-change on-posts-changed)))

(defn build-site []
  ; Bit of a hack to create the folder - better way?
  (jio/make-parents (str dest-posts-path "/x"))
  (convert-all-md-files src-posts-path dest-posts-path)
  (generate-homepage templates-path dest-root-path))

(defn launch-server []
  (run-jetty app {:port 3000}))

(defn -main [& args]
  (build-site)
  (launch-server))
