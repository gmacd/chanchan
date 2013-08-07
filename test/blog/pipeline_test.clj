(ns blog.pipeline-test
  (:require [clojure.test :refer :all]
            [blog.pipeline :refer :all]))

(defn add2 [x] 
  (+ x 2))

(deftest test-adder
  (is (= 24  (add2 22))))

(deftest asset-types-correct
  (testing "Asset types"
    (is (not (nil? (asset-type :post))))))
