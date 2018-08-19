(defproject com.doubleelbow.capital/capital-http "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "https://github.com/doubleelbow/capital-http"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-tools-deps "0.4.1"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:install :user :project]}
  :profiles {:dev {:lein-tools-deps/config {:aliases [:dev]}}})
