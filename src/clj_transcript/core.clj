(ns clj-transcript.core
  "Core API for transcript-based testing.

  A transcript is a markdown file with embedded Clojure code blocks.
  When executed, each code block is evaluated and its output (stdout + result)
  is appended below the block. The resulting markdown is compared against
  a saved snapshot in .clj-transcript/<file>.md."
  (:require [clj-transcript.parser :as parser]
            [clj-transcript.eval :as eval]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def ^:dynamic *transcript-dir*
  "Directory where transcript snapshots are stored."
  ".clj-transcript")

(defn snapshot-path
  "Returns the path to the snapshot file for a given transcript."
  [transcript-path]
  (let [file (io/file transcript-path)
        name (.getName file)]
    (io/file *transcript-dir* name)))

(defn execute-blocks
  "Executes a sequence of code blocks and returns them with outputs."
  [blocks]
  (let [ctx (atom {})]
    (mapv (fn [block]
            (let [result (eval/eval-block (:code block) ctx)
                  output-str (parser/format-output result)]
              (assoc block
                     :output result
                     :output-str output-str)))
          blocks)))

(defn execute-transcript
  "Executes a transcript file and returns the resulting markdown with outputs.
  Returns a map with:
    :source     - original markdown
    :result     - markdown with outputs appended
    :blocks     - individual block results"
  [transcript-path]
  (let [source (slurp transcript-path)
        blocks (parser/parse-code-blocks source)
        results (execute-blocks blocks)
        result-md (parser/render-with-outputs source results)]
    {:source source
     :result result-md
     :blocks results}))

(defn load-snapshot
  "Loads the saved snapshot for a transcript, returns nil if not found."
  [transcript-path]
  (let [snap-file (snapshot-path transcript-path)]
    (when (.exists snap-file)
      (slurp snap-file))))

(defn save-snapshot!
  "Saves a new snapshot for a transcript."
  [transcript-path content]
  (let [snap-file (snapshot-path transcript-path)
        parent (.getParentFile snap-file)]
    (when-not (.exists parent)
      (.mkdirs parent))
    (spit snap-file content)))

(defn compare-blocks
  "Compares source blocks with snapshot blocks.
  Returns a map with:
    :unchanged - blocks where code matches and output matches
    :failed    - blocks where code matches but output differs
    :new       - blocks that are new or have modified code
    :removed   - blocks that were in snapshot but not in source (or code changed)"
  [source-blocks snapshot-blocks]
  (let [max-len (max (count source-blocks) (count snapshot-blocks))
        indices (range max-len)]
    (reduce
     (fn [acc i]
       (let [src (get source-blocks i)
             snap (get snapshot-blocks i)]
         (cond
           ;; Both exist at this position
           (and src snap)
           (let [src-code (str/trim (:code src))
                 snap-code (str/trim (:code snap))
                 src-output (str/trim (or (:output-str src) ""))
                 snap-output (str/trim (or (:output snap) ""))]
             (if (= src-code snap-code)
               ;; Same code - check output
               (if (= src-output snap-output)
                 (update acc :unchanged conj (assoc src :index i))
                 (update acc :failed conj (assoc src
                                                 :index i
                                                 :expected-output snap-output
                                                 :actual-output src-output)))
               ;; Different code - mark as new (src) and removed (snap)
               (-> acc
                   (update :new conj (assoc src :index i))
                   (update :removed conj (assoc snap :index i)))))

           ;; Only source exists - new block
           src
           (update acc :new conj (assoc src :index i))

           ;; Only snapshot exists - removed block
           snap
           (update acc :removed conj (assoc snap :index i)))))
     {:unchanged []
      :failed []
      :new []
      :removed []}
     indices)))

(defn run-transcript
  "Runs a transcript and compares against saved snapshot.
  Returns a map with:
    :path       - transcript file path
    :status     - :pass, :fail, :pending, or :new
    :comparison - detailed block-by-block comparison
    :result     - the execution result markdown"
  [transcript-path]
  (let [source (slurp transcript-path)
        source-blocks (parser/parse-code-blocks source)
        executed-blocks (execute-blocks source-blocks)
        result-md (parser/render-with-outputs source executed-blocks)
        snapshot (load-snapshot transcript-path)]
    (if (nil? snapshot)
      ;; No snapshot exists
      {:path transcript-path
       :status :new
       :comparison {:unchanged []
                    :failed []
                    :new (map-indexed #(assoc %2 :index %1) executed-blocks)
                    :removed []}
       :result result-md}
      ;; Compare with snapshot
      (let [snapshot-blocks (parser/parse-blocks-with-outputs snapshot)
            comparison (compare-blocks executed-blocks snapshot-blocks)
            has-failures (seq (:failed comparison))
            has-changes (or (seq (:new comparison)) (seq (:removed comparison)))]
        {:path transcript-path
         :status (cond
                   has-failures :fail
                   has-changes :pending
                   :else :pass)
         :comparison comparison
         :result result-md}))))

(defn accept-transcript!
  "Runs a transcript and saves the result as the new snapshot."
  [transcript-path]
  (let [{:keys [result]} (execute-transcript transcript-path)]
    (save-snapshot! transcript-path result)
    {:path transcript-path
     :status :accepted
     :result result}))

(defn find-transcripts
  "Finds all transcript files in the given directory (recursively).
  Looks for .md files by default."
  ([dir] (find-transcripts dir #".*\.md$"))
  ([dir pattern]
   (->> (file-seq (io/file dir))
        (filter #(.isFile %))
        (filter #(re-matches pattern (.getName %)))
        (map str)
        (sort))))

(defn run-all
  "Runs all transcripts in the given directory.
  Returns a summary map with results for each transcript."
  [dir]
  (let [transcripts (find-transcripts dir)
        results (mapv run-transcript transcripts)]
    {:total (count results)
     :passed (count (filter #(= :pass (:status %)) results))
     :failed (count (filter #(= :fail (:status %)) results))
     :pending (count (filter #(= :pending (:status %)) results))
     :new (count (filter #(= :new (:status %)) results))
     :results results}))

(defn accept-all!
  "Runs all transcripts and accepts their outputs as new snapshots."
  [dir]
  (let [transcripts (find-transcripts dir)
        results (mapv accept-transcript! transcripts)]
    {:total (count results)
     :accepted (count results)
     :results results}))
