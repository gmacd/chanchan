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

(defn generate-homepage [posts src-path dest-path]
  (spit (str dest-path "/index.html")
        (-> (slurp (str src-path "/index.html"))
            (render {:title "wot?"
                     :body "eh?"})))
  (println "Converted homepage"))

; TODO better fallback - 404?  Is this really an error handler?
(defn handler [request]
  (response "hello world"))

(def app
  (wrap-file handler "site/"))


(defn read-post [post-path]
  {:src-path post-path
   :title post-path
   :body (slurp post-path)})

(defn gather-posts [src-posts-path]
  "Scan all src posts, returning a collection of posts"
  (->> (files-with-extension src-posts-path ".md")
       (map read-post)))

(defn convert-posts [posts dest-posts-path]
  "Given a seq of posts, convert them to html"
  (->> posts
      (map #(assoc % :html (html-post (:title %) (md-to-html-string (:body %)))))
      (map #(assoc % :dest-path (with-ext (with-path (:src-path %) dest-posts-path) "html")))
      (map #(spit (:dest-path %) (:html %)))))


(def src-posts-path "assets/posts")
(def dest-posts-path "site/posts")
(def templates-path "assets/templates")
(def dest-root-path "site")


(defn build-site []
  ; Bit of a hack to create the folder - better way?
  (jio/make-parents (str dest-posts-path "/x"))
  (let [posts (gather-posts src-posts-path)]
    (doseq [p posts] (println "Converted " (.getPath (:src-path p))))
    (convert-posts posts dest-posts-path)
    (generate-homepage posts templates-path dest-root-path)))


(defn- on-posts-changed [post-files]
  "Rebuild site when files changed"
  ; TODO - Currently rebuilds entire site - worth making smarter?  Right now, no!
  ; TODO check file stamp for files newer than start time
  (build-site))

; File watcher future for .md posts
(def post-watcher (watcher src-posts-path
                           (rate 50)
                           (file-filter ignore-dotfiles)
                           (file-filter (extensions :md))
                           (on-change on-posts-changed)))


(defn launch-server []
  (run-jetty app {:port 3000}))

(defn -main [& args]
  (build-site)
  (launch-server))
