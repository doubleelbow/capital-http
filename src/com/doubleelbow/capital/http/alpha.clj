(ns com.doubleelbow.capital.http.alpha
  (:require [com.doubleelbow.capital.alpha :as capital]
            [com.doubleelbow.capital.interceptor.alpha :as interceptor]
            [com.doubleelbow.capital.interceptor.impl.alpha.async-decide :as intc.async-decide]
            [com.doubleelbow.capital.interceptor.impl.alpha.retry :as intc.retry]
            [com.doubleelbow.capital.interceptor.impl.alpha.circuit-breaker :as intc.circuit-breaker]
            [com.doubleelbow.capital.interceptor.impl.alpha.time :as intc.time]
            [com.doubleelbow.capital.interceptor.impl.alpha.init-context :as intc.init-context]
            [com.doubleelbow.capital.interceptor.impl.alpha.response-error :as intc.response-error]
            [pathetic.core :as pathetic]
            [io.pedestal.log :as log]
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
                        (assoc-in context [::capital/request ::url] full-url)))})

(def ^:private catch-error-intc
  {::interceptor/name ::catch-error
   ::interceptor/error (fn [context err]
                         (let [ex (::capital/exception (ex-data err))
                               data (if-let [data (ex-data ex)] data ex)]
                           (log/error :msg "sending request error" :err data)
                           (assoc context ::capital/response {::error? true
                                                              ::data data
                                                              ::full-error err})))})

(defn add-request-fns-intc [config]
  {::interceptor/name ::add-request-fns
   ::interceptor/init {::request-fns (merge {::sync-fn (fn [r] (throw (java.lang.UnsupportedOperationException. "synchronous request function is not implemented")))
                                             ::async-fn (fn [r os oe] (throw (java.lang.UnsupportedOperationException. "asynchronous request function is not implemented")))}
                                            config)}})

(def ^:private sync-request-intc
  {::interceptor/name ::sync-request
   ::interceptor/dependencies ::intc.response-error/response-error
   ::interceptor/up (fn [context]
                      (let [request (::capital/request context)
                            response (apply (get-in context [::request-fns ::sync-fn]) [request])]
                        (log/debug :msg "received response" :req request :resp response)
                        (assoc context ::capital/response response)))})

(def ^:private async-request-intc
  {::interceptor/name ::async-request
   ::interceptor/dependencies ::intc.response-error/response-error
   ::interceptor/up (fn [context]
                      (let [c (async/chan 1)]
                        (apply (get-in context [::request-fns ::async-fn])
                               [(::capital/request context)
                                (fn [resp]
                                  (log/debug :msg "async response" :resp resp)
                                  (async/put! c (assoc context ::capital/response resp)))
                                (fn [err]
                                  (capital/throw-on-channel err c context ::async-request ::interceptor/up))])
                        c))})

(defn initial-context [config]
  (let [interceptors [(full-url-intc (::base-url config))
                      catch-error-intc
                      (intc.init-context/interceptor config [::declare-retriable])
                      (intc.time/interceptor)
                      (intc.retry/interceptor (::intc.retry/config config))
                      (intc.circuit-breaker/interceptor (::intc.circuit-breaker/config config))
                      (intc.response-error/interceptor (fn [context error]
                                                         (let [ex (::capital/exception (ex-data error))]
                                                           (log/debug :msg "checking if retriable" :types (intc.response-error/types error))
                                                           (if (apply (::declare-retriable context (fn [ctx e] true)) [context ex])
                                                             (intc.response-error/add-type error ::intc.response-error/retriable)
                                                             error)))
                                                       (fn [context error]
                                                         (let [ex (::capital/exception (ex-data error))]
                                                           (if (or
                                                                (instance? java.io.IOException ex)
                                                                (intc.response-error/retriable? error))
                                                             (intc.response-error/add-type error ::intc.response-error/transient)
                                                             error))))
                      (add-request-fns-intc (::request-fns config))
                      (intc.async-decide/interceptor {::intc.async-decide/sync-intcs sync-request-intc
                                                      ::intc.async-decide/async-intcs async-request-intc})]]
    (capital/initial-context :capital-http interceptors)))

(defn <send-request! [url request context]
  (log/debug :msg "sending request" :async? (::intc.async-decide/async? request))
  (let [req (-> request
                (assoc ::url url))]
    (capital/<send! req context)))

(defn send-sync-request! [url request context]
  (<!! (<send-request! url (assoc request ::intc.async-decide/async? false) context)))

(defn <send-async-request! [url request context]
  (<send-request! url (assoc request ::intc.async-decide/async? true) context))
