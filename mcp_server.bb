#!/usr/bin/env bb

(ns mcp-server
  (:require [cheshire.core :as json]
            [clojure.string :as str]))

(defn send-message [message]
  ;; Send newline-delimited JSON as per official MCP SDK
  (let [json-str (json/generate-string message)]
    (println json-str)
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
  ;; Basic initialize response with server info and capabilities
  (send-response request-id
                 {:protocolVersion "2025-03-26"
                  :capabilities {:resources {}
                                 :prompts {}
                                 :tools {}}
                  :serverInfo {:name "mcp-example"
                              :version "1.0.0"}})
  ;; Send initialized notification after successful initialization
  (send-notification "notifications/initialized" {}))

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

(defn -main [& args]
  ;; Main message processing loop - read newline-delimited JSON messages
  (loop []
    (when-let [line (read-line)]
      (when (pos? (count line))
        (process-message line))
      (recur))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))