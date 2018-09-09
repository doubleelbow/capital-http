(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [clj-http.client :as http-client]
            [clj-http.fake :as http-fake]
            [com.doubleelbow.capital.alpha :as capital]
            [com.doubleelbow.capital.http.alpha :as capital.http]
            [com.doubleelbow.capital.interceptor.impl.alpha.circuit-breaker :as intc.cb]
            [com.doubleelbow.capital.interceptor.impl.alpha.retry :as intc.retry]
            [com.doubleelbow.capital.interceptor.impl.alpha.async-decide :as intc.async-decide]
            [clojure.core.async :refer [<!!]]))

(comment
  (defn- sync-request [request]
    (-> request
        (assoc :url (::capital.http/url request)
               :async? false)
        (http-client/request)))

  (defn- async-request [request on-success on-error]
    (-> request
        (assoc :url (::capital.http/url request)
               :async? true)
        (http-client/request #(on-success %) #(on-error %))))

  (defn- declare-retriable [retriable-statuses]
    (fn [context ex]
      (let [e (ex-data ex)]
        (not= -1 (.indexOf (get retriable-statuses (:status e 503) [])
                           (get-in context [::capital/request :method]))))))
  
  (def service-ctx (capital.http/initial-context {::capital.http/base-url "http://localhost:8080/"
                                                  ::intc.cb/config {::intc.cb/threshold 10
                                                                    ::intc.cb/interval (* 5 60)
                                                                    ::intc.cb/min-requests 10
                                                                    ::intc.cb/open-duration (* 5 60)}
                                                  ::intc.retry/config {::intc.retry/delay [1000 2000]
                                                                       ::intc.retry/max-retries 3}
                                                  ::capital.http/declare-retriable (declare-retriable {429 [:get :put :delete] 503 [:get :put :delete]})
                                                  ::capital.http/request-fns {::capital.http/sync-fn sync-request
                                                                              ::capital.http/async-fn async-request}}))

  
  )
