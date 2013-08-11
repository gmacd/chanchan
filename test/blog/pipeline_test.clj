(ns blog.pipeline-test
  (:require [clojure.test :refer :all]
            [blog.pipeline :refer :all]
            [clj-time.core :refer [date-time]]))

(defn basic-post []
  (map->Asset {:asset-type :post
               :metadata {}
               :src-path ""
               :title "New Post"
               :body "Post body...."}))
(defn basic-page []
  (map->Asset {:asset-type :page
               :metadata {}
               :src-path ""
               :title "New Page"
               :body "Page body...."}))

(deftest asset-types-correct
  (testing "Asset types"
    (is (= (:post asset-types) (asset-type (basic-post))))
    (is (= (:page asset-types) (asset-type (basic-page))))))

(deftest asset-date
  (let [date (date-time 2013 8 7)
        post (assoc-in (basic-post) [:metadata :date] "2013-08-07")]
    (testing "Can get asset date from asset metadata"
      (is (= date (get-asset-date post))))))

(deftest metadata-parsing
  (testing "Can parse basic metadata"
    (is (= {:abc "123" :def "xyz" :ghi "This is a test"}
           (metadata-string->map "abc:123\ndef:xyz\nghi:This is a test")))
    (is (= {}
           (metadata-string->map "")))
    (is (= {}
           (metadata-string->map nil)))))

(deftest reading-assets
  (testing "Can read basic post"
    (let [content (str "----\n"
                       "title: My Title\n"
                       "----\n"
                       "This is my body\n")
          asset (read-md-asset content :post)]
      (is (= "This is my body"
             (:body asset)))
      (is (= "My Title"
             (:title asset)))
      (is (= :post
             (:asset-type asset)))
      (is (= {:title "My Title"}
             (:metadata asset)))))
  
  (testing "Can read basic page"
    (let [content (str "----\n"
                       "title: My Title\n"
                       "----\n"
                       "This is my body\n")
          asset (read-md-asset content :page)]
      (is (= "This is my body"
             (:body asset)))
      (is (= "My Title"
             (:title asset)))
      (is (= :page
             (:asset-type asset)))
      (is (= {:title "My Title"}
             (:metadata asset)))))
  
    (testing "Only strips first chunk as metadata"
    (let [content (str "----\n"
                       "title: My Title\n"
                       "----\n"
                       "Body Heading\n"
                       "----\n"
                       "This is my body\n")
          asset (read-md-asset content :post)]
      (is (= "Body Heading\n----\nThis is my body"
             (:body asset)))
      (is (= "My Title"
             (:title asset)))
      (is (= :post
             (:asset-type asset)))
      (is (= {:title "My Title"}
             (:metadata asset))))))
