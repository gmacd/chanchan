(defproject blog "0.1.0"
  :description "Minimal static blog generator"
  :url "http://example.com/FIXME"
  :license {:name "Apache License 2.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :main blog.core
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [ring/ring-core "1.2.0"]
                 [ring/ring-jetty-adapter "1.2.0"]
                 [hiccup "1.0.3"]
                 [de.ubercode.clostache/clostache "1.3.1"]
                 [markdown-clj "0.9.29"]
                 [clj-yaml "0.4.0"]
                 [watchtower "0.1.1"]
                 [clj-time "0.5.1"]])
