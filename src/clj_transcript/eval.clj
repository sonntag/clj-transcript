(ns clj-transcript.eval
  "Code evaluation utilities for transcript execution."
  (:require [clojure.string :as str]))

(def ^:private ns-counter (atom 0))

(defn create-eval-ns
  "Creates a fresh namespace for evaluation.
  Uses a unique namespace name to avoid conflicts with other eval contexts."
  []
  (let [id (swap! ns-counter inc)
        ns-name (symbol (str "clj-transcript.sandbox-" id))]
    (create-ns ns-name)
    ns-name))

(defn eval-forms
  "Evaluates a sequence of forms in the given namespace.
  Returns the result of the last form."
  [forms ns-sym]
  (binding [*ns* (the-ns ns-sym)]
    (refer-clojure)
    (reduce (fn [_ form]
              (eval form))
            nil
            forms)))

(defn read-all-forms
  "Reads all forms from a string of Clojure code."
  [code]
  (let [reader (java.io.PushbackReader. (java.io.StringReader. code))]
    (loop [forms []]
      (let [form (try
                   (read {:eof ::eof} reader)
                   (catch Exception e
                     (throw (ex-info "Read error" {:code code} e))))]
        (if (= form ::eof)
          forms
          (recur (conj forms form)))))))

(defn eval-block
  "Evaluates a code block string, capturing stdout and the result.
  The ctx atom is used to maintain state across blocks (shared namespace).

  Returns a map with:
    :result - the value of the last expression
    :stdout - captured standard output
    :error  - error message if evaluation failed"
  [code ctx]
  (let [ns-sym (or (:ns @ctx)
                   (let [ns (create-eval-ns)]
                     (swap! ctx assoc :ns ns)
                     ns))
        stdout-capture (java.io.StringWriter.)]
    (try
      (let [forms (read-all-forms code)
            result (binding [*out* stdout-capture]
                     (eval-forms forms ns-sym))]
        {:result result
         :stdout (str stdout-capture)
         :error nil})
      (catch Exception e
        {:result nil
         :stdout (str stdout-capture)
         :error (or (.getMessage e)
                    (str (class e)))}))))
