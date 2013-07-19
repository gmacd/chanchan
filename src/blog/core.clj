(ns blog.core
  (:require [clojure.java.io :as jio]
            [clojure.string :as string])
  (:use [markdown.core :only (md-to-html-string)]
        clostache.parser
        watchtower.core
        ring.adapter.jetty
        ring.util.response
        [ring.middleware resource file file-info])
  (:import (java.nio.file Files LinkOption)
           (java.nio.file.attribute BasicFileAttributes)))

(defn file-attributes [file]
  "Return the file BasicFileAttributes of file.  File can be a file or a string
   (or anything else acceptable to jio/file)"
  (Files/readAttributes (.toPath (jio/file file)) BasicFileAttributes (into-array LinkOption [])))

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

; TODO better fallback - 404?  Is this really an error handler?
(defn handler [request]
  (response "hello world"))

(def app
  (wrap-file handler "site/"))

(defn metadata-string->map [metadata]
  "Given string of several lines of form 'key1:value1', return map of kvps."
  (let [metadata (if (nil? metadata) "" metadata)]
    (let [kvp-strings (map #(string/split % #":") (string/split metadata #"[\r\n]+"))]
      (into {}
            (for [[k v] kvp-strings :when (not (string/blank? k))]
              [(keyword k) ((fnil string/trim "") v)])))))

(defn read-post [post-path]
  "Read in a post from post-path. Content should be split by '----' alone on a
   line.  If two parts exist, first part is considered metadata, one per line,
   keys and values seperated by ':'.  Second part is Markdown content.  If no
   split, entire message is considered Markdown content."
  (let [contents (slurp post-path)
        [md raw-metadata] (reverse (string/split contents #"[\r\n]+-+[\r\n]+" 2))
        metadata (metadata-string->map raw-metadata)]
    {:meta metadata
     :src-path post-path
     :title (:title metadata)
     :body md}))

(defn gather-posts [src-posts-path]
  "Scan all src posts, returning a collection of posts"
  (->> (files-with-extension src-posts-path ".md")
       (map read-post)))

; TODO dest-path should be a file - currently it's a string
(defn convert-posts [posts dest-posts-path]
  "Given a seq of posts, convert them to html"
  (letfn [(html-filename [file] (with-ext (.getName file) "html"))]
    (map #(assoc %
            :html (md-to-html-string (:body %))
            :dest-path (str dest-posts-path "/" (html-filename (:src-path %)))
            :url (str "/posts/" (html-filename (:src-path %))))
         posts)))


(defn write-posts [posts templates-path]
  "Output all posts"
  (let [post-template (slurp (str templates-path "/post.html"))]
    (doseq [post posts]
      (spit (:dest-path post)
            (render post-template
                    {:title (:title post)
                     :post (:html post)})))))

; TODO title should be data driven
; TODO make generic page generator?
(defn generate-homepage [posts src-path dest-path]
  (spit (str dest-path "/index.html")
        (-> (slurp (str src-path "/index.html"))
            (render {:title "wot?"
                     :posts posts})))
  (println "Converted homepage"))


(def src-posts-path "assets/posts")
(def dest-posts-path "site/posts")
(def templates-path "assets/templates")
(def dest-root-path "site")


(defn build-site []
  ; Bit of a hack to create the folder - better way?
  (jio/make-parents (str dest-posts-path "/x"))
  (let [posts (-> (gather-posts src-posts-path)
                  (convert-posts dest-posts-path))]
    (write-posts posts templates-path)
    (doseq [p posts] (println "Converted" (:dest-path p)))
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
