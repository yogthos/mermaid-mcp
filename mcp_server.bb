#!/usr/bin/env bb

(ns mcp-server
  (:require [cheshire.core :as json]
            [clojure.string :as str]))

(defn send-message [message]
  (let [json-str (json/generate-string message)
        message-str (str "Content-Length: " (count json-str) "\r\n\r\n" json-str)]
    (print message-str)
    (flush)))

(defn send-response [request-id result]
  (send-message {:jsonrpc "2.0"
                 :id request-id
                 :result result}))

(defn send-notification [method params]
  (send-message {:jsonrpc "2.0"
                 :method method
                 :params params}))

(defn send-error [request-id code message]
  (send-message {:jsonrpc "2.0"
                 :id request-id
                 :error {:code code
                         :message message}}))

(defn handle-initialize [request-id params]
  ;; Basic initialize response with minimal capabilities
  (send-response request-id
                 {:protocolVersion "2025-03-26"
                  :capabilities {:resources {}
                                 :prompts {}
                                 :tools {}}}))

(defn handle-echo [request-id params]
  ;; Echo back the parameters as the result
  (send-response request-id params))

(defn handle-ping [request-id params]
  ;; Simple ping response
  (send-response request-id {:pong true}))

(defn handle-request [message]
  (let [method (:method message)
        params (:params message)
        request-id (:id message)]
    (case method
      "initialize" (handle-initialize request-id params)
      "echo" (handle-echo request-id params)
      "ping" (handle-ping request-id params)
      ;; Default error response for unknown methods
      (send-error request-id -32601 (str "Method not found: " method)))))

(defn process-message [message-str]
  (try
    (let [message (json/parse-string message-str keyword)]
      (cond
        ;; Handle requests (messages with 'id')
        (:id message)
        (handle-request message)

        ;; Handle notifications (messages without 'id')
        (:method message)
        ;; For now, we'll just ignore notifications
        nil

        :else
        ;; Invalid message format
        (println "Invalid message format:" message-str)))
    (catch Exception e
      (println "Error processing message:" message-str ", error:" (ex-message e)))))

(defn read-jsonrpc-message
  "Read a complete JSON-RPC message with Content-Length framing"
  []

  (try
    ;; Read all input at once
    (let [input (slurp System/in)]
      ;; Split by the message separator (\r\n\r\n)
      (when-let [[_ body] (str/split input #"\r\n\r\n" 2)]
        body))
    (catch Exception e
      nil)))

(defn -main [& args]
  ;; Send initialized notification
  (send-notification "notifications/initialized" {})

  ;; Main message processing loop
  (when-let [message-str (read-jsonrpc-message)]
    (process-message message-str)))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))