(ns blog.pipeline
  (:require [clojure.java.io :as jio]
            [clojure.string :as string]
            [markdown.core :refer [md-to-html-string]]
            [clostache.parser :refer [render]]
            [blog.file :refer [file-attributes with-ext files-with-extension]])
  (:import (java.text SimpleDateFormat ParsePosition)
           (java.util Date)))

; TODO Load templates and cache them
; TODO Directly add all metadata into map for replacement?

(def templates-path "assets/templates")
(def dest-root-path "site")

; TODO Remove the :src-path and :dest-path from the asset record & use asset-type
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

(defn metadata-string->map [metadata]
  "Given string of several lines of form 'key1:value1', return map of kvps."
  (let [metadata (if (nil? metadata) "" metadata)]
    (let [kvp-strings (map #(string/split % #":") (string/split metadata #"[\r\n]+"))]
      (into {}
            (for [[k v] kvp-strings :when (not (string/blank? k))]
              [(keyword k) ((fnil string/trim "") v)])))))

; TODO Pull metadata up into asset level?
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
     ; Need :title so replace-vars step can access it for asset collection
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

(defn preprocess-asset [asset-path asset-type]
  "First step of the pipeline, given an asset path and its type, returns an
   asset record."
  (-> (read-md-asset asset-path)
      (assoc :asset-type asset-type)
      (prepare-asset-for-export (:dest-path (asset-type asset-types)))))

(defn preprocess-assets [asset-type]
  "Given an asset type, return a collection of all asset records of that type."
  (map #(preprocess-asset % asset-type)
       (files-with-extension (:src-path (asset-type asset-types)) ".md")))

(defn replace-vars [text asset all-assets]
  "Return the given text, with {{foo}} vars replaced."
  (render text
          {:title (:title asset)
           :date (-> (SimpleDateFormat. "d MMMM yyy") (.format (get-asset-date asset)))
           :asset (:body asset)
           :posts (:posts all-assets)
           :pages (:pages all-assets)}))

; TODO Split two replacement operations?
(defn replace-asset-variables [asset all-assets templates-path]
  "First replace vars in the body of the asset, then in the asset once it's
   been insert into its asset type template."
  ; Replace vars in the asset body
  (let [asset (assoc asset
                :body (replace-vars (:body asset) asset all-assets))
        post-template (slurp (str templates-path "/" (:template (asset-type asset))))]
    ; Now replace vars in the asset type body
    (assoc asset
      :replaced-asset (replace-vars post-template asset all-assets))))

(defn export-asset-as-html [asset templates-path]
  (let [body (md-to-html-string (:replaced-asset asset))
        html-template (slurp (str templates-path "/default.html"))
        html (render html-template
                     {:title (:title asset)
                      :body body})]
    (spit (:dest-path asset) html)))


(defn build-site []
  ; Bit of a hack to create the folders - better way?
  (map #(jio/make-parents (str % "/x"))
       [(:dest-path (:post asset-types)) (:dest-path (:page asset-types))])

  ; Preprocess all assets
  (let [posts (preprocess-assets :post)
        pages (preprocess-assets :page)
        all-assets {:posts posts :pages pages}]

    ; Variable replacement
    (let [all-assets-for-export
          (map #(replace-asset-variables % all-assets templates-path)
               (concat posts pages))]
      (doall (map #(export-asset-as-html % templates-path)
                  all-assets-for-export)))))
