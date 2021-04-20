# Alia

Alia's goal is to be a very simple to use library without trading
performance, features or extensibility.

It allows do to everything
[datastax/java-driver](https://github.com/datastax/java-driver) has to offer
with an idiomatic API, from a handful of functions. The learning
curve or need to reach for the docs should be minimal.

Alia also comes with [Hayt](#hayt-query-dsl) a CQL query DSL inspired
by korma/ClojureQL.

This guide is far from complete, but it should give you an idea of how
to get started. For advanced uses please use the
[api docs](http://mpenet.github.io/alia/qbits.alia.html)), or ping me
on IRC (#clojure on freenode), slack (clojurians) or the mailing list,
I am always glad to help.

## Cluster initialisation

To get started you will need to prepare a cluster instance, so that
you can create sessions from it and interact with multiple keyspaces.

```clojure

(require '[qbits.alia :as alia])

(def cluster (alia/cluster))
```
`alia/cluster` can take a number of optional parameters:

ex:
```clojure
(def cluster (alia/cluster {:contact-points ["192.168.1.30" "192.168.1.31" "192.168.1.32"]
                            :port 9042}))

```

The following options are supported:

* `:contact-points` : List of nodes ip addresses to connect to.

* `:port` : port to connect to on the nodes (native transport must be
  active on the nodes: `start_native_transport: true` in
  cassandra.yaml). Defaults to 9042 if not supplied.

* `:load-balancing-policy` : Configure the
  [Load Balancing Policy](http://mpenet.github.io/alia/qbits.alia.policy.load-balancing.html)
  to use for the new cluster.

* `:reconnection-policy` : Configure the
  [Reconnection Policy](http://mpenet.github.io/alia/qbits.alia.policy.reconnection.html)
  to use for the new cluster.

* `:retry-policy` : Configure the
  [Retry Policy](http://mpenet.github.io/alia/qbits.alia.policy.retry.html)
  to use for the new cluster.

* `:metrics?` : Toggles metrics collection for the created cluster
  (metrics are enabled by default otherwise).

* `:jmx-reporting?` : Toggles JMX reporting of the metrics.

* `:credentials` : Takes a map of :user and :password for use with
  Cassandra's PasswordAuthenticator

* `:compression` : Compression supported by the Cassandra binary
  protocol. Can be `:none`, `:snappy` or `:lz4`.

* `:ssl?`: enables/disables SSL

* `:ssl-options` : advanced SSL setup using a
  `com.datastax.driver.core.SSLOptions` instance

* `:pooling-options` : The pooling options used by this builder.
  Options related to connection pooling.

  The driver uses connections in an asynchronous way. Meaning that
  multiple requests can be submitted on the same connection at the
  same time. This means that the driver only needs to maintain a
  relatively small number of connections to each Cassandra host. These
  options allow to control how many connections are kept exactly.

  For each host, the driver keeps a core amount of connections open at
  all time. If the utilisation of those connections reaches a
  configurable threshold ,more connections are created up to a
  configurable maximum number of connections.

  Once more than core connections have been created, connections in
  excess are reclaimed if the utilisation of opened connections drops
  below the configured threshold.

  Each of these parameters can be separately set for `:local` and `:remote`
  hosts (HostDistance). For `:ignored` hosts, the default for all those
  settings is 0 and cannot be changed.

  Each of the following configuration keys, take a map of {distance value}  :
  ex:
  ```clojure
  :core-connections-per-host {:remote 10 :local 100}
  ```

  + `:core-connections-per-host` Number
  + `:max-connections-per-host` Number
  + `:max-simultaneous-requests-per-connection` Number
  + `:min-simultaneous-requests-per-connection` Number

* `:socket-options`: a map of
  + `:connect-timeout-millis` Number
  + `:read-timeout-millis` Number
  + `:receive-buffer-size` Number
  + `:send-buffer-size` Number
  + `:so-linger` Number
  + `:tcp-no-delay?` Bool
  + `:reuse-address?` Bool
  + `:keep-alive?` Bool

* `:query-options`: a map of
  + `:page-size` Number
  + `:consistency` (consistency Keyword)
  + `:serial-consistency` (consistency Keyword)

* `:jmx-reporting?` Bool, enables/disables JMX reporting of the metrics.


The handling of these options is achieved with a multimethod that you
could extend if you need to handle some special case or want to create
your own options templates.

There are also a few more options such as custom NettyOptions,
Speculative Execution Policy & co. See
`qbits.alia.cluster-options/set-cluster-option!`
[[source]](../src/qbits/alia/cluster_options.clj#L19)

## Retry, Reconnection, Load balancing policies

These are all available from `qbits.alia.policy.*`.

Consult the codox documentation for details about these, they are
described in detail:

* [Load Balancing Policies](http://mpenet.github.io/alia/qbits.alia.policy.load-balancing.html)

* [Reconnection Policies](http://mpenet.github.io/alia/qbits.alia.policy.reconnection.html)

* [Retry Policies](http://mpenet.github.io/alia/qbits.alia.policy.retry.html)

* [Speculative Execution](http://mpenet.github.io/alia/qbits.alia.policy.speculative-execution.html)

* [Address Translater](http://mpenet.github.io/alia/qbits.alia.policy.address-translater.html)

## Creating Sessions from a cluster instance

A session holds connections to a Cassandra cluster, allowing to query
it. Each session will maintain multiple connections to the cluster
nodes, and provides policies to choose which node to use for each
query (token-aware by default), handles
retries for failed query (when it makes sense), etc...

Session instances are thread-safe and usually a single instance is
enough per application. However, a given session can only be set to
one keyspace at a time, so one instance per keyspace is necessary.

```clojure
(def session (alia/connect cluster))
```
or if you want to use a particular keyspace from the start:

```clojure
(def session (alia/connect cluster "demokeyspace"))
```


## Executing queries

You can interact with C* using either raw queries or prepared statements.
There are three function that allow you to do that: `alia/execute`,
`alia/execute-async`,  `alia/execute-chan`, `alia/execute-chan-buffered`.

These functions support a number of options, but the simplest example
of its use would look like this:

```clojure
(alia/execute session "SELECT * FROM foo;")
```

And it would return:

```clojure

 >> ({:created nil,
      :last_name "Baggins",
      :emails #{"baggins@gmail.com" "f@baggins.com"},
      :tags [4 5 6],
      :first_name "Frodo",
      :amap {"foo" 1, "bar" 2},
      :auuid #uuid "1f84b56b-5481-4ee4-8236-8a3831ee5892",
      :valid true,
      :birth_year 1,
      :user_name "frodo"})
```

As you can see C* datatypes are translated to clojure friendly types
when it's possible: in this example `:emails` is a C* native Set,
`:tags` is a native list and `:amap` is a native map.

### Asynchronous queries

The previous examples will block until a response is received from
cassandra. But it is possible to avoid that and perform them asynchronously.

You have 3 options here:

* core.async interface (which is the recommended way)

* ztellman/manifold optional interface

* plain old callback based interface

#### Callback based interface

You will need to use `execute-async` which returns a future and accepts
2 callbacks via its options, one for :success one for :error

```clojure
(alia/execute-async session "SELECT * FROM foo;" {:success (fn [r] ...) :error (fn [e] ...)})
```

You can also just deref the return value as it is a future, but it's
almost never what you want.

#### clojure/core.async asynchronous interface

`alia/execute-chan` has the same signature as the other execute
functions and as the name implies returns a clojure/core.async
promise-chan that will contain a list of rows at some point or an
exception instance.

Once you run it you have a couple of options to pull data from it.

+ using `clojure.core.async/take!` which takes the channel as first argument
and a callback as second:

```clojure
(take! (execute-chan session "select * from users;")
       (fn [rows-or-exception]
         (do-something rows)))
```

+ using `clojure.core.async/<!!` to block and pull the rows/exception
  from the channel.

```clojure
(def rows-or-exception (<!! (execute-chan session "select * from users;")))
```

+ using `clojure.core.async/go` block potentially using
  `clojure.core.async/alt!`.

```clojure
(go
  (loop [i 0 ret []]
    (if (= 3 i)
      ret
      (recur (inc i)
             (conj ret (<! (execute-chan session "select * from users limit 1")))))))
```

`alia/execute-chan-buffered` is a different beast, it allows
controlled streaming of rows in a channel (1 channel value = 1
row). When you setup your query the channel buffer will inherit the
fetch size of the context and allow you to control when/how to
retrieve the streamed results the server is sending you and exert
backpressure. You could this way retrieve a very large dataset and
handle it in a streaming fashion.

### Prepared statements

Prepared statements still use `alia/execute` or `alia/execute-async`,
but require 1 (optionally 2) more steps.

In order to prepare a statement you need to use `alia/prepare`

#### Positional parameters

```clojure
(def statement (alia/prepare session "SELECT * FROM foo WHERE foo=? AND bar=?;"))
```


```clojure
(alia/execute session statement {:values ["value-of-foo" "value-of-bar"]})
```

#### With named parameters

```clojure
(def statement (alia/prepare session "SELECT * FROM foo WHERE foo= :foo AND bar= :bar;"))
```


```clojure
(alia/execute session statement {:values {:foo "value-of-foo" :bar "value-of-bar"}})
```
#### qbits.alia/bind advanced uses

Alternatively you can bind values prior to execution (in case the
value don't change often and you don't want this step to be repeated at
query type for every call to `execute` or `execute-async`).

```clojure
(def bst (alia/bind statement ["value-of-foo" "value-of-bar"]))
(alia/execute session bst)
```

You don't have to deal with translations of data types, this is
done under the hood.

#### UDT and Tuple

If you have too use UDT or Tuples you will have to create custom
encoder functions for them. This is very easy:

```clojure
(def ->user (qbits.alia/udt-encoder session "mykeyspace" "user"))

;; and then you can use this function to create valid UDTValues:
(alia/execute session statement {:values [(->user {:name "Max Penet" :age 38})]})
```

Same for Tuples

```clojure
(def ->point (alia/udt-encoder session "mykeyspace" "point"))

;; and then you can use this function to create valid UDTValues:
(alia/execute session statement {:values [(->point [1 2])]})
```

You can easily mix them:

```clojure
(def ->address (qbits.alia/udt-encoder session "mykeyspace" "address"))
(def ->user (alia/udt-encoder session "mykeyspace" "user"))

(alia/execute session statement {:values [(->user {:name "Max Penet" :age 38 :address (->address {:street "..."})})]})
```

### Batching

You can batch queries using CQL directly (with or without hayt), we
also support a feature of the driver that allows to batch any query
time (prepared, simple, etc).

```clojure
(alia/execute session (alia/batch ...))
```

`batch` takes a collection of queries, they can be of any type
accepted by execute, raw string, hayt query, prepared statement etc.

If you're batching prepared statements be aware that you cannot bind
values via :values in execute, if you need to do so use
qbits.alia/bind on your statements separately.

```clojure
(alia/execute session (alia/batch [(bind stmt ["foo]) ...]))
```

Keep you batch size relatively small, it's not advised to send huge
amounts of queries this way.

### `alia/execute` & its variants advanced options

The complete signature of execute looks like this

`execute`, `execute-chan`, `execute-chan-buffered` and `execute-async`
support a number of options I didn't mention earlier, you can specify
* `:consistency` [Consistency](#consistency)
* `:retry-policy` [Retry Policy](http://mpenet.github.io/alia/qbits.alia.policy.retry.html)
* `:routing-key` [RoutingKey](#routing-key)
* `:tracing?` (boolean) triggers tracing (defaults to false)
* `:string-keys?` (boolean, defaults false) stringify keys (they are
   keywords by default, can be handy to prevent filling PermGen when
   dealing with compact storage "wide rows").
* `:page-size` (int) sets max number of rows returned from server at a time.


Some of execute functions have specific options, see
[api docs](http://mpenet.github.io/alia/qbits.alia.html)).

#### Consistency

Here are the supported consistency and serial-consistency levels:

`:each-quorum, :one, :local-quorum, :quorum, :three, :all, :serial, :two, :local-serial, :local-one, :any`

```clojure
(alia/execute session bst {:consistency :all})
```

You can also set the consistency globaly at the cluster level via
`qbits.alia/cluster` options.

#### Routing key

You can manually provide a routing key for this query. It is thus
optional since the routing key is only an hint for token aware load
balancing policy but is never mandatory.

RoutingKey on datastax doc : http://www.datastax.com/drivers/java/apidocs/com/datastax/driver/core/SimpleStatement.html#setRoutingKey(java.nio.ByteBuffer...)

#### Retry Policy

Sets the retry policy to use for this query.
The default retry policy, if this option is not used, is the one
returned by Policies.getRetryPolicy() in the cluster
configuration. This method is thus only useful in case you want to
punctually override the default policy for this request.

[Retry Policies](http://mpenet.github.io/alia/qbits.alia.policy.retry.html)

## Shutting down

To clean up the resources used by alia once you are done, you can call
`alia/close` on the session.

```clojure
(alia/close session)
```
## Extending data type support

If you want alia to be able to encode custom data types without having
to do it yourself for every query you can extend the following
protocol `qbits.alia.codec/PCodec`'s `encode` function.

Here is an example that is provided for supporting joda-time.

```clojure
(ns qbits.alia.codec.joda-time
  (:require [qbits.alia.codec.default :as default-codec]))

(extend-protocol default-codec/Encode
  org.joda.time.DateTime
  (encode [x]
    (.toDate x)))
```

You can do the same via `default-codec/Encode`
Decoding is automatic for any cql type, including tuples and UDTs.

### Custom Codecs

This is using the default codec Alia comes bundled with, but you can
easily create your own specialized codec and use it with/without the
default one. The default does the "right" thing in most cases: it will
properly handle collections types, decode udt/tuples and do all this
in a recursive manner (a map of udts with tuple values will be decoded
nicely).

A codec is simply a map or record of :encoder/:decoder functions that
can be passed to `execute`/`bind`/`batch`. By default alia will get
deserialized values from java-driver, which is fine in most cases but
UDT/Tuple and other composite types are a bit non-clojure friendly.

The simplest Codec would be:

```clojure
(def id-codec {:encoder identity :decoder identity})
```

Which could in turned be used:

```clojure
(alia/execute session "select * from foo;" {:codec id-codec})
```


Another Codec that comes with alia is `qbits.alia.codec.udt-aware/codec`. It
allows you to register Record instances to cassandra UDTs:

```clojure
(require '[qbits.alia.codec.udt-aware :as udt-aware])

(defrecord Foo [a b])
(defrecord Bar [a b])

(udt-aware/register-udt! udt-aware/codec Foo)
(udt-aware/register-udt! udt-aware/codec Bar)

(alia/execute session "select foo, bar from "foo-table" limit 1" {:codec udt-aware/codec})

=> [{:foo #user.Foo{:a "Something" :b "Else"
     :bar #user.Bar{:a "Meh" :b "Really"}}]
```

This is done at decoding time, so there's no extra iteration involved,
and this can be applied on streaming queries just fine as a result.


## Row Generators

Row generators allow you to control how Row values are accumulated
into a single unit. By default alia will create one (transient) map
per row, but you might like vectors intead, or you could want to apply
some computation at this level, this is just what they enable.

The default Generator is defined as follows:

```clojure
  (reify qbits.alia.codec/RowGenerator
    (init-row [_] (transient {}))
    (conj-row [_ row k v] (assoc! row (keyword k) v))
    (finalize-row [_ row] (persistent! row)))
```

We also supply 2 other Generators, one that creates vector pairs instead of maps:

`qbits.alia.codec/row-gen->vector`

``` clojure
(alia/execute session "select * from foo" {:row-generator row-gen->vector})
```

and another one that creates Records from Rows instead of maps

`qbits.alia.codec/row-gen->record`

``` clojure
(alia/execute session "select * from foo" {:row-generator (row-gen->record map->Foo)})
```

## Hayt: Query DSL

Alia comes with the latest version of [Hayt](https://github.com/mpenet/hayt).
This is a query DSL, that is composable, very easy to use, performant
and provides complete support for CQL3 features.

See [codox documentation](http://mpenet.github.com/hayt/codox/qbits.hayt.html) or
[Hayt's tests](https://github.com/mpenet/hayt/blob/master/test/qbits/hayt/core_test.clj).

Alia supports Hayt query direct execution, if you pass a non-compiled
query, it will be compiled and cached.

The same is true with `prepare`, the query gets prepared (ignoring the
values, it's for convenience really, it compiles the query under the
hood and only passes the parameterized string with ? placeholders).
```clojure
(prepare session (select :user (where {:foo "bar"})))

;; positional parameter
(prepare session (select :user (where {:bar ?})))

;; named parameter
(prepare session (select :user (where {:bar :bar})))
```

Ex:
```clojure
(execute session (select :users (where {:name "foo"})))
```
You can have control over the query caching using
`set-hayt-query-fn!` and provide your own memoize implementation or you
can set its value to `qbits.hayt/->raw` if you prefer not to use query caching.
The default uses `clojure.core.memoize` with a LU cache with a `:threshold`
of 100.

## `blob` data: `byte-array`s

Cassandra `blob` columns can store Java `byte-array` data.

Ex:

```clojure
(defn- update-byte-array!
  [session id ^bytes ba]
  (let [statement (alia/prepare session "INSERT into t (id, ba) VALUES (?, ?);")]
    (alia/execute session statement {:values [id ba]})))
```

Notably, Alia returns the column data as a `java.nio.ByteBuffer`, but conversion back to a `byte-array` can be accomplished by calling `.array:
 - https://docs.oracle.com/javase/8/docs/api/java/nio/ByteBuffer.html#array--

For a working example, see `blob-test` in: [../test/qbits/alia_test.clj](../test/qbits/alia_test.clj).
