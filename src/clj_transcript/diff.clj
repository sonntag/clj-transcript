(ns clj-transcript.diff
  "Diff utilities for comparing transcript outputs."
  (:require [clojure.string :as str]))

;; ANSI color codes
(def ^:private colors
  {:red     "\u001b[31m"
   :green   "\u001b[32m"
   :yellow  "\u001b[33m"
   :cyan    "\u001b[36m"
   :reset   "\u001b[0m"
   :bold    "\u001b[1m"})

(defn- colorize [color text]
  (str (get colors color "") text (:reset colors)))

(defn- lcs-table
  "Builds a longest common subsequence table for two sequences."
  [a b]
  (let [m (count a)
        n (count b)
        table (make-array Long/TYPE (inc m) (inc n))]
    (doseq [i (range (inc m))]
      (aset table i 0 (long 0)))
    (doseq [j (range (inc n))]
      (aset table 0 j (long 0)))
    (doseq [i (range 1 (inc m))
            j (range 1 (inc n))]
      (if (= (nth a (dec i)) (nth b (dec j)))
        (aset table i j (long (inc (aget table (dec i) (dec j)))))
        (aset table i j (long (max (aget table (dec i) j)
                                   (aget table i (dec j)))))))
    table))

(defn- backtrack-diff
  "Backtracks through LCS table to produce diff operations."
  [table a b i j]
  (cond
    (and (zero? i) (zero? j))
    []

    (and (pos? i) (pos? j) (= (nth a (dec i)) (nth b (dec j))))
    (conj (backtrack-diff table a b (dec i) (dec j))
          {:op :equal :line (nth a (dec i))})

    (and (pos? j)
         (or (zero? i)
             (>= (aget table i (dec j)) (aget table (dec i) j))))
    (conj (backtrack-diff table a b i (dec j))
          {:op :add :line (nth b (dec j))})

    :else
    (conj (backtrack-diff table a b (dec i) j)
          {:op :remove :line (nth a (dec i))})))

(defn diff-lines
  "Computes a line-by-line diff between two strings.
  Returns a sequence of {:op :equal|:add|:remove, :line string}."
  [expected actual]
  (let [a (str/split-lines expected)
        b (str/split-lines actual)
        table (lcs-table a b)]
    (backtrack-diff table a b (count a) (count b))))

(defn format-diff
  "Formats a diff for terminal output with colors."
  [diff-ops]
  (let [format-line (fn [{:keys [op line]}]
                      (case op
                        :equal  (str "  " line)
                        :remove (colorize :red (str "- " line))
                        :add    (colorize :green (str "+ " line))))]
    (->> diff-ops
         (map format-line)
         (str/join "\n"))))

(defn format-diff-summary
  "Formats a compact diff showing only changed sections with context."
  [expected actual & {:keys [context] :or {context 3}}]
  (let [diff-ops (diff-lines expected actual)
        indexed (map-indexed vector diff-ops)
        change-indices (->> indexed
                            (filter #(not= :equal (:op (second %))))
                            (map first)
                            set)]
    (if (empty? change-indices)
      nil
      (let [;; Expand to include context lines
            expanded-indices
            (set (for [idx change-indices
                       offset (range (- context) (inc context))
                       :let [i (+ idx offset)]
                       :when (and (>= i 0) (< i (count diff-ops)))]
                   i))

            ;; Group consecutive indices into ranges
            sorted-indices (sort expanded-indices)
            groups (reduce
                    (fn [acc idx]
                      (if (empty? acc)
                        [[idx]]
                        (let [last-group (peek acc)
                              last-idx (peek last-group)]
                          (if (= (inc last-idx) idx)
                            (conj (pop acc) (conj last-group idx))
                            (conj acc [idx])))))
                    []
                    sorted-indices)

            format-group
            (fn [indices]
              (let [start (first indices)
                    lines (for [i indices
                                :let [{:keys [op line]} (nth diff-ops i)]]
                            (case op
                              :equal  (str "  " line)
                              :remove (colorize :red (str "- " line))
                              :add    (colorize :green (str "+ " line))))]
                (str (colorize :cyan (str "@@ line " (inc start) " @@"))
                     "\n"
                     (str/join "\n" lines))))]
        (str/join "\n\n" (map format-group groups))))))

(defn print-diff
  "Prints a formatted diff between expected and actual content."
  [expected actual]
  (println)
  (println (colorize :bold "Diff (expected vs actual):"))
  (println)
  (if-let [diff-output (format-diff-summary expected actual)]
    (println diff-output)
    (println "  (no differences found)")))
