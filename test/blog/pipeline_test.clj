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
