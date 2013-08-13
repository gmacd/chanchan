(ns blog.pipeline
  (:require [clojure.java.io :as jio]
            [clojure.string :as string]
            [markdown.core :refer [md-to-html-string]]
            [clostache.parser :refer [render render-resource]]
            [clj-time.coerce :refer [to-long from-long from-date]]
            [clj-time.format :refer [formatter unparse]]
            [clj-yaml.core :refer [parse-string]]
            [blog.file :refer [file-attributes with-ext files-with-extension]]))

; TODO Load templates and cache them
; TODO Post sorting?
; TODO Could I improve this with dynamic private vars?
;      Seems a suitable place.
;      e.g. (def ^:private ^:dynamic *assets*) ; Or asset?
;           (binding [*assets* (my-asset)] (blah))
;      Then can use it without passing it into funcs
; TODO System object to store all app state -> core
;      Can have constructor for cmd line app, one for dev repl use, etc.
; TODO Google Analytics

(def display-date-formatter (formatter "dd MMMM yyy"))

; Load up the site settings
(def config-settings (parse-string (slurp "config.yml")))

(def ^:const dest-root-path "site")

; TODO Remove the :src-path and :dest-path from the asset record & use asset-type
(def ^:const asset-types
  {:post {:src-path "assets/posts"
          :dest-path "site/posts"
          :url "/posts/"
          :template "templates/post_wrapper.html"}
   :page {:src-path "assets/pages"
          :dest-path "site/pages"
          :url "/pages/"
          :template "templates/page_wrapper.html"}})

(defn asset-type [asset]
  "Return the asset-type record for a given asset"
  ((:asset-type asset) asset-types))

(defn get-asset-date [asset]
  "Given an asset record, extract the date from the metadata if possible,
  in format YYYY-MM-DD, otherwise get the creation date of the src asset."
  (let [date (:date (:metadata asset))]
    (if (nil? date)
      (-> (file-attributes (:src-path asset)) (.creationTime) (.toMillis) (from-long))
      (from-date date))))

(defrecord Asset [asset-type metadata src-path title body])

; TODO Pull metadata up into asset level?
(defn read-md-asset [asset-contents asset-type]
  "Read in a post or page from src-asset-path. Metadata is wrapped by '----'
   above and  below.  If two parts exist, first part is considered metadata, one
   per line, keys and values seperated by ':'.  Second part is Markdown content.
   If no split, entire message is considered Markdown content."
  (let [[raw-metadata md] (filter #(not (empty? %))
                                  (string/split asset-contents #"(?m)^-+$" 3))
        metadata (parse-string raw-metadata)]
    (if (and (not (nil? raw-metadata)) (not (nil? md)))
      (map->Asset {:asset-type asset-type
                   :metadata metadata
                   ; Need :title so replace-vars step can access it for asset collection
                   :title (:title metadata)
                   :body (string/trim md)})
      (println " Asset doesn't declare both metadata and a body"))))

; TODO dest-path should be a file - currently it's a string
(defn prepare-asset-for-export [asset src-path dest-directory]
  "Given a seq of processed md assets, convert them to html"
  (let [html-filename (with-ext (.getName src-path) "html")
        asset (assoc asset
                :src-path src-path
                :dest-path (str dest-directory "/" html-filename)
                :url (str (:url (asset-type asset)) html-filename))
        date (get-asset-date asset)]
    (assoc asset
      :date date
      :display-date (unparse display-date-formatter date))))

(defn preprocess-asset [asset-path asset-type dest-directory]
  "First step of the pipeline, given an asset path and its type, returns an
   asset record."
  (println " Importing:" (.getCanonicalPath asset-path))
  (-> (slurp asset-path)
      (read-md-asset asset-type)
      (prepare-asset-for-export asset-path dest-directory)))

(defn preprocess-assets [asset-type blog-path]
  "Given an asset type, return a collection of all asset records of that type."
  (let [src-path (str blog-path "/" (:src-path (asset-type asset-types)))
        dest-path (str blog-path "/" (:dest-path (asset-type asset-types)))]
    (map #(preprocess-asset % asset-type dest-path)
       (files-with-extension src-path ".md"))))

; TODO Split two replacement operations?
(defn replace-asset-variables [asset]
  "First replace vars in the body of the asset, then in the asset once it's
   been insert into its asset type template."
  ; Replace vars in the asset body
  (let [asset (assoc asset
                :body (-> (:body asset)
                          (render asset)
                          (md-to-html-string)))
        post-template (slurp (jio/resource (:template (asset-type asset))))]
    
    ; Now replace vars in the asset type body
    (assoc asset :body (render post-template asset))))

(defn export-asset-as-html [asset]
  (let [html (render-resource "templates/default.html" asset)]
    (println " Exporting" (:dest-path asset))
    (spit (:dest-path asset) html)))

(defn build-site [blog-dir]
  (println "Building blog:" blog-dir)
  
  ; Preprocess all assets
  (let [posts (preprocess-assets :post blog-dir)
        pages (preprocess-assets :page blog-dir)
        posts-by-date (sort-by #(to-long (:date %)) > posts)
        all-assets (map #(merge (assoc %
                                  :posts-by-date posts-by-date
                                  :pages pages)
                                config-settings) (concat posts pages))
        all-assets-for-export (map #(replace-asset-variables %) all-assets)]
    (doall (map #(export-asset-as-html %) all-assets-for-export))

    ; If index.html exists, copy it to the root folder
    (let [index-page (first (filter #(.endsWith (:url %) "/pages/index.html") all-assets))]
      (if-not (nil? index-page)
        (jio/copy (jio/file (:dest-path index-page)) (jio/file "site/index.html"))))))
