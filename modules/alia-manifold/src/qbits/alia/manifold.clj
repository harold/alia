(ns qbits.alia.manifold
  (:require
   [manifold.deferred :as d]
   [manifold.stream :as s]
   [qbits.alia :as alia])
  (:import
   [com.datastax.oss.driver.api.core.session Session]
   [com.datastax.oss.driver.api.core CqlSession]
   [com.datastax.oss.driver.api.core.cql AsyncResultSet]
   [java.util.concurrent CompletionStage]))

(defn execute
  "Same as qbits.alia/execute, but returns just the first page of results"
  ([^CqlSession session query {:as opts}]
   (d/chain
    (alia/execute-async session query opts)
    :current-page))
  ([^Session session query]
     (execute session query {})))

(defn handle-page-completion-stage
  [^CompletionStage completion-stage
   {statement :statement
    values :values
    stream :stream
    executor :executor
    :as opts}]
  (alia/handle-completion-stage
   completion-stage

   (fn [{current-page :current-page
        ^AsyncResultSet async-result-set :async-result-set
        next-page-handler :next-page-handler
        :as val}]

     (d/chain
      (s/put! stream current-page)
      (fn [put?]
        (cond

          ;; last page put ok and there is another
          (and put?
               next-page-handler)
          (d/chain
           (.fetchNextPage async-result-set)
           next-page-handler
           #(handle-page-completion-stage % opts))

          ;; last page put ok and was the last
          put?
          (s/close! stream)

          ;; bork! last page did not put.
          ;; maybe the stream was closed?
          :else
          (throw
           (ex-info
            "qbits.alia.manifold/stream-put!-fail"
            (merge val (select-keys opts [:statement :values]))))))))

   (fn [err]
     (d/chain
      (s/put! stream err)
      (d/finally
        (fn [] (s/close! stream)))))

   opts))

(defn execute-buffered-pages
  ([^CqlSession session query {stream :stream
                               buffer-size :buffer-size
                               :as opts}]
   (let [stream (or stream
                    ;; fetch one page ahead by default
                    (s/stream (or buffer-size 1)))

         page-cs (alia/execute-async session query opts)]

     (handle-page-completion-stage
      page-cs
      (merge opts
             {:stream stream
              :statement query}))

     stream))

  ([^CqlSession session query]
   (execute-buffered-pages session query {})))

(defn execute-buffered
  ([^CqlSession session query {:as opts}]
   (let [stream (execute-buffered-pages session query opts)]

     (s/transform
      (mapcat identity)
      stream)))

  ([^CqlSession session query]
   (execute-buffered session query {})))
