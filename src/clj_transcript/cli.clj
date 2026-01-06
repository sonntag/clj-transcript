(ns clj-transcript.cli
  "Command-line interface for clj-transcript."
  (:require [clj-transcript.core :as core]
            [clj-transcript.diff :as diff]
            [clojure.string :as str])
  (:gen-class))

(def ^:dynamic *transcript-dir*
  "Default directory to search for transcripts."
  "transcripts")

;; ANSI color codes
(def ^:private colors
  {:red     "\u001b[31m"
   :green   "\u001b[32m"
   :yellow  "\u001b[33m"
   :cyan    "\u001b[36m"
   :gray    "\u001b[90m"
   :reset   "\u001b[0m"
   :bold    "\u001b[1m"})

(defn- colorize [color text]
  (str (get colors color "") text (:reset colors)))

(defn- truncate-code
  "Truncates code to first line if multiline, max 60 chars."
  [code]
  (let [first-line (first (str/split-lines (str/trim code)))
        truncated (if (> (count first-line) 60)
                    (str (subs first-line 0 57) "...")
                    first-line)]
    (if (str/includes? code "\n")
      (str truncated " ...")
      truncated)))

(defn- print-failed-block
  "Prints details of a failed block (output mismatch)."
  [{:keys [index code expected-output actual-output]}]
  (println)
  (println (colorize :red (str "  Block " (inc index) " output mismatch:")))
  (println (colorize :gray (str "    " (truncate-code code))))
  (println)
  (println (str "    " (colorize :bold "Expected:")))
  (doseq [line (str/split-lines expected-output)]
    (println (colorize :red (str "      " line))))
  (println)
  (println (str "    " (colorize :bold "Actual:")))
  (doseq [line (str/split-lines actual-output)]
    (println (colorize :green (str "      " line)))))

(defn- print-new-block
  "Prints details of a new block."
  [{:keys [index code]}]
  (println (colorize :green (str "    + Block " (inc index) ": " (truncate-code code)))))

(defn- print-removed-block
  "Prints details of a removed block."
  [{:keys [index code]}]
  (println (colorize :red (str "    - Block " (inc index) ": " (truncate-code code)))))

(defn print-result
  "Prints the result of running a single transcript."
  [{:keys [path status comparison]}]
  (let [icon (case status
               :pass "PASS"
               :fail "FAIL"
               :pending "PENDING"
               :new "NEW"
               :accepted "ACCEPTED"
               "?")]
    (println (str "[" icon "] " path))

    (when (= status :fail)
      (doseq [block (:failed comparison)]
        (print-failed-block block))
      (println)
      (println (str "  Run 'transcript accept " path "' to update the snapshot")))

    (when (or (= status :pending) (= status :new))
      (when (seq (:new comparison))
        (println)
        (println (colorize :cyan "  New blocks:"))
        (doseq [block (:new comparison)]
          (print-new-block block)))
      (when (seq (:removed comparison))
        (println)
        (println (colorize :cyan "  Removed blocks:"))
        (doseq [block (:removed comparison)]
          (print-removed-block block)))
      (println)
      (println (str "  Run 'transcript accept " path "' to accept changes")))))

(defn print-summary
  "Prints a summary of test results."
  [{:keys [total passed failed pending new]}]
  (println)
  (println (str "Results: " total " total, "
                passed " passed, "
                failed " failed, "
                pending " pending, "
                new " new")))

(defn cmd-run
  "Run transcripts."
  [args]
  (if (empty? args)
    (let [summary (core/run-all *transcript-dir*)]
      (doseq [result (:results summary)]
        (print-result result))
      (print-summary summary)
      (if (or (pos? (:failed summary))
              (pos? (:pending summary))
              (pos? (:new summary)))
        1
        0))
    (let [path (first args)
          result (core/run-transcript path)]
      (print-result result)
      (if (or (= :fail (:status result))
              (= :pending (:status result))
              (= :new (:status result)))
        1
        0))))

(defn cmd-accept
  "Accept a single transcript."
  [args]
  (if (empty? args)
    (do
      (println "Usage: transcript accept <file-path>")
      (println "  Or use 'transcript accept-all' to accept all transcripts")
      1)
    (let [path (first args)
          result (core/accept-transcript! path)]
      (print-result result)
      0)))

(defn cmd-accept-all
  "Accept all transcripts."
  [_args]
  (let [summary (core/accept-all! *transcript-dir*)]
    (doseq [result (:results summary)]
      (print-result result))
    (println)
    (println (str "Accepted " (:accepted summary) " transcript(s)"))
    0))

(defn cmd-help
  "Print help message."
  [_args]
  (println "clj-transcript - Transcript-based testing for Clojure")
  (println)
  (println "Usage: transcript <command> [args]")
  (println)
  (println "Commands:")
  (println "  run              Run all registered transcript files")
  (println "  run <file>       Run a specific transcript file")
  (println "  accept <file>    Run a transcript and accept its output")
  (println "  accept-all       Run all transcripts and accept all outputs")
  (println "  help             Show this help message")
  (println)
  (println "Transcript files are searched in the 'transcripts/' directory.")
  (println "Snapshots are stored in '.clj-transcript/'.")
  0)

(defn -main
  [& args]
  (let [cmd (first args)
        cmd-args (rest args)
        exit-code (case cmd
                    ("run" nil) (cmd-run cmd-args)
                    "accept" (cmd-accept cmd-args)
                    "accept-all" (cmd-accept-all cmd-args)
                    ("help" "-h" "--help") (cmd-help cmd-args)
                    (do
                      (println (str "Unknown command: " cmd))
                      (cmd-help nil)
                      1))]
    (System/exit exit-code)))
