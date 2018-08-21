(defproject com.doubleelbow.capital/capital-http "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "https://github.com/doubleelbow/capital-http"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[com.doubleelbow.capital/capital "0.1.0-SNAPSHOT"]
                 [io.pedestal/pedestal.log "0.5.4"]
                 [clj-http "3.9.1"]
                 [pathetic "0.5.1"]]
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.3.0-alpha1"]
                                  [ch.qos.logback/logback-classic "1.1.7" :exclusions [org.slf4j/slf4j-api]]
                                  [org.slf4j/jul-to-slf4j "1.7.21"]
                                  [org.slf4j/jcl-over-slf4j "1.7.21"]
                                  [org.slf4j/log4j-over-slf4j "1.7.21"]
                                  [org.clojure/data.json "0.2.6"]
                                  [clj-http-fake "1.0.3"]]}})
