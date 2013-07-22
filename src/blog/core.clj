(ns blog.core
  (:require [clojure.java.io :as jio]
            [clojure.string :as string])
  (:use [markdown.core :only (md-to-html-string)]
        clostache.parser
        watchtower.core
        ring.adapter.jetty
        ring.util.response
        [ring.middleware resource file file-info])
  (:import (java.text SimpleDateFormat ParsePosition)
           (java.util Date)
           (java.nio.file Files LinkOption)
           (java.nio.file.attribute BasicFileAttributes)))

; TODO Remove ring dependencies
; TODO Move generic file-related function

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

(defn read-md-asset [src-asset-path]
  "Read in a post or page from src-asset-path. Content should be split by '----'
   alone on a line.  If two parts exist, first part is considered metadata, one
   per line, keys and values seperated by ':'.  Second part is Markdown content.
   If no split, entire message is considered Markdown content."
  (let [contents (slurp src-asset-path)
        [md raw-metadata] (reverse (string/split contents #"[\r\n]+-+[\r\n]+" 2))
        metadata (metadata-string->map raw-metadata)]
    {:metadata metadata
     :src-path src-asset-path
     :title (:title metadata)
     :body md}))

(defn gather-md-assets [src-path]
  "Scan all md files, returning a collection of records containing the processed
   md file and any metadata."
  (->> (files-with-extension src-path ".md")
       (map read-md-asset)))

; TODO - will a new SimpleDateFormat and ParsePosition get created each time this is called?
; Create func for parsing date and memoize parser?
(defn get-asset-date [asset]
  "Given an asset record, extract the date from the metadata if possible,
  in format YYYY-MM-DD, otherwise get the creation date of the src asset."
  (let [date (:date (:metadata asset))]
    (if (nil? date)
      (-> (file-attributes (:src-path asset)) (.creationTime) (.toMillis) (Date.))
      (-> (SimpleDateFormat. "yyyy-MM-dd") (.parse date (ParsePosition. 0))))))

; TODO dest-path should be a file - currently it's a string
(defn prepare-asset-for-export [assets dest-path]
  "Given a seq of processed md assets, convert them to html"
  (letfn [(html-filename [file] (with-ext (.getName file) "html"))]
    (map #(assoc %
;            :html (md-to-html-string (:body %))
            :dest-path (str dest-path "/" (html-filename (:src-path %)))
            :url (str "/posts/" (html-filename (:src-path %))))
         assets)))

; TODO Run templating before conerting to html!
(defn write-assets [assets-to-write all-assets templates-path]
  "Output all processed assets"
  (let [post-template (slurp (str templates-path "/post.html"))]
    (doseq [asset assets-to-write]
      (spit (:dest-path asset)
            (render post-template
                    {:title (:title asset)
                     :date (-> (SimpleDateFormat. "d MMMM yyy") (.format (get-asset-date asset)))
                     :asset (:html asset)
                     :posts (:posts all-assets)
                     :pages (:pages all-assets)})))))

; TODO title should be data driven
; TODO make generic page generator?
(defn generate-homepage [posts src-path dest-path]
  (spit (str dest-path "/index.html")
        (-> (slurp (str src-path "/index.html"))
            (render {:title "wot?"
                     :posts posts})))
  (println "Converted homepage"))


(def src-posts-path "assets/posts")
(def src-pages-path "assets/pages")
(def dest-posts-path "site/posts")
(def dest-pages-path "site/pages")
(def templates-path "assets/templates")
(def dest-root-path "site")


; TODO Write function to process asset
(defn build-site []
  ; Bit of a hack to create the folders - better way?
  (map #(jio/make-parents (str % "/x"))
       [dest-posts-path dest-pages-path])
  
  (let [posts (-> (gather-md-assets src-posts-path)
                  (prepare-asset-for-export dest-posts-path))
        pages (-> (gather-md-assets src-pages-path)
                  (prepare-asset-for-export dest-pages-path))
        all-assets {:posts posts :pages pages}]
    (write-assets posts all-assets templates-path)
    (write-assets pages all-assets templates-path)
    (doseq [p posts] (println "Converted" (:dest-path p)))
    (doseq [p pages] (println "Converted" (:dest-path p)))))
    ;(generate-homepage posts templates-path dest-root-path)))


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
