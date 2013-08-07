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
; TODO Post sorting?
; TODO Could I improve this with dynamic private vars?
;      Seems a suitable place.
;      e.g. (def ^:private ^:dynamic *assets*) ; Or asset?
;           (binding [*assets* (my-asset)] (blah))
;      Then can use it without passing it into funcs
; TODO System object to store all app state -> core
;      Can have constructor for cmd line app, one for dev repl use, etc.

(def ^:const templates-path "assets/templates")
(def ^:const dest-root-path "site")

; TODO Remove the :src-path and :dest-path from the asset record & use asset-type
(def ^:const asset-types
  {:post {:src-path "assets/posts"
          :dest-path "site/posts"
          :url "/posts/"
          :template "post_wrapper.md"}
   :page {:src-path "assets/pages"
          :dest-path "site/pages"
          :url "/pages/"
          :template "page_wrapper.md"}})

(defn asset-type [asset]
  "Return the asset-type record for a given asset"
  ((:asset-type asset) asset-types))

; TODO - will a new SimpleDateFormat and ParsePosition get created each time this is called?
; Create func for parsing date and memoize parser?
(defn get-asset-date [asset]
  "Given an asset record, extract the date from the metadata if possible,
  in format YYYY-MM-DD, otherwise get the creation date of the src asset."
  (let [date (:date (:metadata asset))]
    (if (nil? date)
      (-> (file-attributes (:src-path asset)) (.creationTime) (.toMillis) (Date.))
      (-> (SimpleDateFormat. "yyyy-MM-dd") (.parse date (ParsePosition. 0))))))

(defn metadata-string->map [metadata]
  "Given string of several lines of form 'key1:value1', return map of kvps."
  (let [metadata (if (nil? metadata) "" metadata)]
    (let [kvp-strings (map #(string/split % #":") (string/split metadata #"[\r\n]+"))]
      (into {}
            (for [[k v] kvp-strings :when (not (string/blank? k))]
              [(keyword k) ((fnil string/trim "") v)])))))

(defrecord Asset [asset-type metadata src-path title body])

; TODO Pull metadata up into asset level?
(defn read-md-asset [src-asset-path asset-type]
  "Read in a post or page from src-asset-path. Metadata is wrapped by '----'
   above and  below.  If two parts exist, first part is considered metadata, one
   per line, keys and values seperated by ':'.  Second part is Markdown content.
   If no split, entire message is considered Markdown content."
  (println " Importing:" (.getCanonicalPath src-asset-path))
  (let [contents (slurp src-asset-path)
        [md raw-metadata] (reverse (filter #(not (empty? %))
                                           (string/split contents #"(?m)^-+$")))
        metadata (metadata-string->map raw-metadata)]
    (map->Asset {:asset-type asset-type
                 :metadata metadata
                 :src-path src-asset-path
                 ; Need :title so replace-vars step can access it for asset collection
                 :title (:title metadata)
                 :body md})))

; TODO dest-path should be a file - currently it's a string
(defn prepare-asset-for-export [asset dest-path]
  "Given a seq of processed md assets, convert them to html"
  (let [html-filename (with-ext (.getName (:src-path asset)) "html")]
    (assoc asset
      :dest-path (str dest-path "/" html-filename)
      :url (str (:url (asset-type asset)) html-filename)
      :date (-> (SimpleDateFormat. "d MMMM yyy") (.format (get-asset-date asset))))))

(defn preprocess-asset [asset-path asset-type dest-path]
  "First step of the pipeline, given an asset path and its type, returns an
   asset record."
  (-> (read-md-asset asset-path asset-type)
      (prepare-asset-for-export dest-path)))

(defn preprocess-assets [asset-type blog-path]
  "Given an asset type, return a collection of all asset records of that type."
  (let [src-dir (str blog-path "/" (:src-path (asset-type asset-types)))
        dest-path (str blog-path "/" (:dest-path (asset-type asset-types)))]
    (map #(preprocess-asset % asset-type dest-path)
       (files-with-extension src-dir ".md"))))

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
        post-template (slurp (jio/resource "templates/page_wrapper.md"))]
    ; Now replace vars in the asset type body
    (assoc asset
      :replaced-asset (replace-vars post-template asset all-assets))))

(defn export-asset-as-html [asset templates-path]
  (let [body (md-to-html-string (:replaced-asset asset))
        html-template (slurp (str templates-path "/default.html"))
        html (render html-template
                     {:title (:title asset)
                      :body body})]
    (println " Exporting" (:dest-path asset))
    (spit (:dest-path asset) html)))

(defn build-site [blog-dir]
  (println "Building blog:" blog-dir)
  
  ; Preprocess all assets
  (let [posts (preprocess-assets :post blog-dir)
        pages (preprocess-assets :page blog-dir)
        all-assets {:posts posts :pages pages}]

    ; Variable replacement
    (let [all-assets-for-export
          (map #(replace-asset-variables % all-assets templates-path)
               (concat posts pages))]
      (doall (map #(export-asset-as-html % templates-path)
                  all-assets-for-export)))

    ; If index.html exists, copy it to the root folder
    (let [index-page (first (filter #(.endsWith (:url %) "/pages/index.html") pages))]
      (if-not (nil? index-page)
        (jio/copy (jio/file (:dest-path index-page)) (jio/file "site/index.html"))))))
