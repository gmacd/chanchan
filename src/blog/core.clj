(ns blog.core
  [:require [clojure.java.io :as jio]]
  [:use [markdown.core :only [md-to-html-string]]]
  [:use [hiccup core page]])


(defn html-post [title body]
  (html5 [:head
          [:link {:rel "stylesheet" :type "text/css" :href "../bootstrap/css/bootstrap.css"}]
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

(defn convert-md-file [src dest]
  (let [md-contents (slurp src)]
    (->> md-contents
         (md-to-html-string)
         (html-post "post?")
         (spit dest))))

(defn convert-all-md-files [src-path dest-path]
  (let [src (jio/file src-path)
        dest (jio/file dest-path)
        md-files (files-with-extension src-path ".md")]
    (doseq [md-file md-files]
      (let [dest-path (str dest-path "/" (with-ext (.getName md-file) "html"))]
        (convert-md-file md-file dest-path)))))

(defn -main [& args]
  (convert-all-md-files "assets/posts" "site/posts"))