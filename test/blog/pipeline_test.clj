(ns blog.pipeline-test
  (:require [clojure.test :refer :all]
            [blog.pipeline :refer :all]))

(def basic-post (map->Asset {:asset-type :post
                          :metadata {}
                          :src-path ""
                          :title "New Post"
                          :body "Post body...."}))
(def basic-page (map->Asset {:asset-type :page
                             :metadata {}
                             :src-path ""
                             :title "New Page"
                             :body "Page body...."}))

(deftest asset-types-correct
  (testing "Asset types"
    (is (= (:post asset-types) (asset-type basic-post)))
    (is (= (:page asset-types) (asset-type basic-page)))))
