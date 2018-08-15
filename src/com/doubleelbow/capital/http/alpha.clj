(ns com.doubleelbow.capital.http.alpha
  (:require [com.doubleelbow.capital.alpha :as capital]
            [com.doubleelbow.capital.interceptor.alpha :as interceptor]
            [com.doubleelbow.capital.interceptor.impl.async-decide :as intc.async-decide]
            [com.doubleelbow.capital.interceptor.impl.circuit-breaker :as intc.circuit-breaker]
            [com.doubleelbow.capital.interceptor.impl.time :as intc.time]
            [pathetic.core :as pathetic]
            [io.pedestal.log :as log]
            [clj-http.client :as http-client]
            [clojure.core.async :refer [<!!] :as async])
  (:import [java.net MalformedURLException]))

(defn- url? [val]
  (try
    (pathetic/as-url val)
    true
    (catch MalformedURLException e false)))

(defn- full-url-intc [base-url]
  {::interceptor/name ::full-url
   ::interceptor/init {::base-url (pathetic/url-normalize base-url)}
   ::interceptor/up (fn [context]
                      (let [req (::capital/request context)
                            base-url (::base-url context)
                            req-url (::url req)
                            full-url (if (url? req-url)
                                       req-url
                                       (pathetic/url-normalize (str base-url req-url)))]
                        (assoc-in context [::capital/request :url] full-url)))})

(def ^:private catch-error-intc
  {::interceptor/name ::catch-error
   ::interceptor/error (fn [context err]
                         (log/error :msg "something went wrong" :err err)
                         (assoc context ::capital/response "error thrown ..."))})

(def ^:private error-response-intc
  {::interceptor/name ::error-response
   ::interceptor/error (fn [context error]
                         (let [ex (::capital/exception (ex-data error))
                               transient? (instance? java.io.IOException ex)]
                           (log/info :msg "error response" :type (type ex) :isTransient transient?)
                           (assoc context ::capital/error (ex-info (.getMessage error) (merge (ex-data error) {::capital/exception-type (if transient? ::intc.circuit-breaker/transient ::capital/unknown)}))))
                         )})

(def ^:private sync-request-intc
  {::interceptor/name ::sync-request
   ::interceptor/dependencies ::error-response
   ::interceptor/up (fn [context]
                      (let [request (merge (::capital/request context) {:async? false})
                            response (http-client/request request)]
                        (log/debug :msg "received response" :req request :resp response)
                        (assoc context ::capital/response response)))})

(def ^:private async-request-intc
  {::interceptor/name ::async-request
   ::interceptor/dependencies ::error-response
   ::interceptor/up (fn [context]
                      (let [c (async/chan 1)]
                        (http-client/request (merge (::capital/request context) {:async? true})
                                             (fn [resp]
                                               (log/debug :msg "async response" :resp resp)
                                               (async/put! c (assoc context ::capital/response resp)))
                                             (fn [err]
                                               (capital/throw-on-channel err c context ::async-request ::interceptor/up)))
                        c))})

(defn initial-context [config]
  (let [interceptors [(full-url-intc (::base-url config))
                      catch-error-intc
                      (intc.time/time-intc)
                      (intc.circuit-breaker/circuit-breaker-intc (::intc.circuit-breaker/config config))
                      error-response-intc
                      (intc.async-decide/async-decide-intc {::intc.async-decide/sync-intcs sync-request-intc
                                                            ::intc.async-decide/async-intcs async-request-intc})]]
    (capital/initial-context :capital-http interceptors)))

(defn send-request! [url request context]
  (log/debug :msg "sending request" :async? (::intc.async-decide/async? request))
  (let [req (-> request
                (assoc ::url url
                       :socket-timeout 1000))]
    (<!! (capital/<send! req context))))

(defn send-sync-request! [url request context]
  (send-request! url (assoc request ::intc.async-decide/async? false) context))

(defn send-async-request! [url request context]
  (send-request! url (assoc request ::intc.async-decide/async? true) context))
