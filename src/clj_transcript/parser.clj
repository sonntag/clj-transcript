(ns clj-transcript.parser
  "Markdown parsing utilities for extracting and rendering code blocks."
  (:require [clojure.string :as str]))

(def code-block-pattern
  "Regex pattern to match fenced code blocks with optional language."
  #"(?m)^```(\w*)\n([\s\S]*?)^```")

(defn- output-block?
  "Returns true if the content looks like an output block."
  [content]
  (boolean (re-find #"^(;;=>|;; stdout:|;; error:)" (str/trim content))))

(defn parse-all-blocks
  "Parses markdown and extracts all fenced code blocks.
  Returns a vector of maps with :lang, :content, :start, :end, :is-output."
  [markdown]
  (let [matcher (re-matcher code-block-pattern markdown)]
    (loop [blocks []]
      (if (.find matcher)
        (let [lang (.group matcher 1)
              content (.group matcher 2)
              start (.start matcher)
              end (.end matcher)
              is-output (and (str/blank? lang) (output-block? content))]
          (recur (conj blocks {:lang lang
                               :content content
                               :start start
                               :end end
                               :is-output is-output})))
        blocks))))

(defn parse-code-blocks
  "Parses markdown and extracts all Clojure code blocks (excluding outputs).
  Returns a vector of maps with :lang, :code, :start, :end positions."
  [markdown]
  (->> (parse-all-blocks markdown)
       (filter #(and (not (:is-output %))
                     (or (= (:lang %) "clojure") (= (:lang %) "clj"))))
       (mapv #(-> %
                  (assoc :code (:content %))
                  (dissoc :content :is-output)))))

(defn parse-blocks-with-outputs
  "Parses a snapshot markdown and extracts code blocks paired with their outputs.
  Returns a vector of maps with :code and :output (the output block content)."
  [markdown]
  (let [all-blocks (parse-all-blocks markdown)]
    (loop [blocks all-blocks
           result []]
      (if (empty? blocks)
        result
        (let [block (first blocks)
              rest-blocks (rest blocks)]
          (if (and (not (:is-output block))
                   (or (= (:lang block) "clojure") (= (:lang block) "clj")))
            ;; This is a code block, check if next block is its output
            (let [next-block (first rest-blocks)
                  has-output (and next-block (:is-output next-block))]
              (recur (if has-output (rest rest-blocks) rest-blocks)
                     (conj result {:code (str/trim (:content block))
                                   :output (when has-output
                                             (str/trim (:content next-block)))})))
            ;; Skip non-clojure blocks
            (recur rest-blocks result)))))))

(defn format-output
  "Formats the output of a code block execution for display."
  [{:keys [stdout result error]}]
  (let [parts (cond-> []
                (and stdout (not (str/blank? stdout)))
                (conj (str ";; stdout:\n" (str/trim stdout)))

                error
                (conj (str ";; error: " error))

                (and (not error) (some? result))
                (conj (str ";;=> " (pr-str result))))]
    (when (seq parts)
      (str/join "\n" parts))))

(defn render-with-outputs
  "Takes the original markdown and block results, returns markdown with outputs.
  Outputs are inserted as comments after each code block."
  [markdown blocks]
  (if (empty? blocks)
    markdown
    (let [sorted-blocks (sort-by :end > blocks)]
      (reduce
       (fn [md block]
         (let [output (format-output (:output block))]
           (if (and output (not (str/blank? output)))
             (let [insert-pos (:end block)
                   before (subs md 0 insert-pos)
                   after (subs md insert-pos)]
               (str before "\n\n" "```\n" output "\n```" after))
             md)))
       markdown
       sorted-blocks))))

(defn strip-outputs
  "Removes output blocks that were previously added.
  Output blocks are identified by starting with ;;=> or ;; stdout: or ;; error:"
  [markdown]
  (str/replace markdown
               #"\n\n```\n(;;=>|;; stdout:|;; error:)[\s\S]*?```"
               ""))
