(ns blog.file
  (:require [clojure.java.io :as jio])
  (:import (java.nio.file Files LinkOption)
           (java.nio.file.attribute BasicFileAttributes)))

; TODO Move to generic (Java7?) file-related function

(defn file-attributes [file]
  "Return the file BasicFileAttributes of file.  File can be a file or a string
   (or anything else acceptable to jio/file)"
  (Files/readAttributes (.toPath (jio/file file))
                        BasicFileAttributes
                        (into-array LinkOption [])))

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
