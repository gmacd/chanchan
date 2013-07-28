(ns blog.core
  (:require [clojure.java.io :as jio]
            [clojure.string :as string]
            [markdown.core :refer [md-to-html-string]]
            [clostache.parser :refer [render]]
            [watchtower.core :refer [watcher on-change file-filter rate ignore-dotfiles extensions]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.response :refer [response]]
            [ring.middleware.file :refer [wrap-file]])
  (:import (java.text SimpleDateFormat ParsePosition)
           (java.util Date)
           (java.nio.file Files LinkOption)
           (java.nio.file.attribute BasicFileAttributes)))

; TODO Remove ring dependencies
; TODO Move server/watch related code to seperate file
; TODO Move generic file-related function
; TODO Load templates and cache them
; TODO Directly add all metadata into map for replacement?

(def asset-types
  {:post {:src-path "assets/posts"
          :dest-path "site/posts"
          :template "post.md"}
   :page {:src-path "assets/pages"
          :dest-path "site/pages"
          :template "page.md"}})

(defn asset-type [asset]
  "Return the asset-type record for a given asset"
  ((:asset-type asset) asset-types))

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
(defn prepare-asset-for-export [asset dest-path]
  "Given a seq of processed md assets, convert them to html"
  (let [html-filename (with-ext (.getName (:src-path asset)) "html")]
    (assoc asset
      :dest-path (str dest-path "/" html-filename)
      :url (str "/posts/" html-filename))))

(defn replace-asset-variables [asset all-assets templates-path]
  (assoc asset
    :replaced-asset 
    (let [post-template (slurp (str templates-path "/" (:template (asset-type asset))))]
      (render post-template
              {:title (:title asset)
               :date (-> (SimpleDateFormat. "d MMMM yyy") (.format (get-asset-date asset)))
               :asset (:body asset)
               :posts (:posts all-assets)
               :pages (:pages all-assets)}))))

(defn export-asset-as-html [asset templates-path]
  (let [body (md-to-html-string (:replaced-asset asset))
        html-template (slurp (str templates-path "/default.html"))
        html (render html-template
                     {:title (:title asset)
                      :body body})]
    (spit (:dest-path asset) html)))

(def templates-path "assets/templates")
(def dest-root-path "site")


(defn build-site []
  ; Bit of a hack to create the folders - better way?
  (map #(jio/make-parents (str % "/x"))
       [(:dest-path (:post asset-types)) (:dest-path (:page asset-types))])

  ; Process pipeline func
  (letfn [(preprocess-asset [asset asset-type]
            (-> (read-md-asset asset)
                (assoc :asset-type asset-type)
                (prepare-asset-for-export (:dest-path (asset-type asset-types)))))]
    
    ; Preprocess all assets
    (let [posts (map #(preprocess-asset % :post)
                     (files-with-extension (:src-path (:post asset-types)) ".md"))
          pages (map #(preprocess-asset % :page)
                     (files-with-extension (:src-path (:page asset-types)) ".md"))
          all-assets {:posts posts :pages pages}]

      ; Variable replacement
      (let [all-assets-for-export
            (map #(replace-asset-variables % all-assets templates-path)
                 (concat posts pages))]
        (doall (map #(export-asset-as-html % templates-path)
                    all-assets-for-export))))))


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


(defn launch-server []
  (run-jetty app {:port 3000}))

(defn -main [& args]
  (build-site)
  (launch-server))
