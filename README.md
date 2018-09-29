# capital http [![Clojars Project](https://img.shields.io/clojars/v/com.doubleelbow.capital/capital-http.svg)](https://clojars.org/com.doubleelbow.capital/capital-http)

Capital-http is a clojure library built on top of [capital](https://github.com/doubleelbow/capital) for handling HTTP requests.

## Usage

Simple usage examples can be found in `dev/user.clj` and at [capital example](https://github.com/doubleelbow/capital-example).

Service context should be created first with a call to `initial-context` function which can then be passed to one of the send request functions along with absolute or relative url and http client's request map. Currently any http client library can be used with capital-http (see [the future](#future)).

```clojure
;; config map
;; intc.cb is alias for com.doubleelbow.capital.interceptor.impl.alpha.circuit-breaker
;; intc.retry is alias for com.doubleelbow.capital.interceptor.impl.alpha.retry
{::base-url "http://localhost:8080/"
 ::intc.cb/config cb-config
 ::intc.retry/config retry-config
 ::declare-retriable declare-retriable-fn
 ::request-fns request-fns-map}
```

`::base-url` should be a string url which is used as a base when send requests functions are called with relative url.

For correct definitions of cb-config and retry-config see documentation for [circuit breaker](https://github.com/doubleelbow/capital/blob/master/doc/interceptors/circuit_breaker.md) and [retry](https://github.com/doubleelbow/capital/blob/master/doc/interceptors/retry.md) interceptors.

`::declare-retriable` should be a 2-arity function that receives `context` and `exception` and should return true if `exception` is deemed retriable.

```clojure
;; request-fns-map
{::sync-fn sync-request
 ::async-fn async-request}
```

`::sync-fn` should be a 1-arity function that executes request synchronously and returns the response based on incoming `request` map parameter.

`::async-fn` should be a 3-arity function that receives `request` map along with `on-success` and `on-error` callbacks. It should execute request asynchronously and call one of the callbacks with response when the response is available.

## Future

Decision was made not to depend on any http client library inside capital-http so that it can be used with existing projects. As a consequence of this decision:

1. A work of creating actual request functions is delegated to developers using capital-http which might be a call for additional capital libraries for each http client library (clj-http, http-kit, &hellip;).
1. The future of capital-http is somewhat unsure. It seems like very similar library can be used for executing database queries. Hence, interceptors defined in capital-http would be moved down to capital rendering capital-http obsolete and then the natural need for libraries described in 1. would occur.

## Contributing

If you've found a bug or have an idea for new feature in mind, please start by creating a github issue. All contributions are welcome.

New features will be considered, but with [unsure future](#future) of this project they might not be implemented anytime soon.

## License

Copyright © 2018 doubleelbow

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
