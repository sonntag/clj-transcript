# clj-transcript API Reference

This transcript serves as both documentation and a test suite for the clj-transcript library.

## Parser Functions

The `clj-transcript.parser` namespace provides functions for parsing markdown files and extracting code blocks.

```clojure
(require '[clj-transcript.parser :as parser]
         '[clojure.string :as str])
```

### Parsing Code Blocks

`parse-code-blocks` extracts Clojure code blocks from markdown text. It returns a vector of maps containing the code, language, and position information.

We'll create a sample markdown string using a helper to avoid nested fence issues:

```clojure
(def sample-md
  (str "# Example\n\n"
       "~~~clojure\n(+ 1 2)\n~~~\n\n"
       "Some text here.\n\n"
       "~~~clojure\n(* 3 4)\n~~~\n"))
```

Note: We use `~~~` as an alternative fence in the test string since triple-backticks would conflict with the transcript format.

Let's parse with backtick-style fences using a properly constructed string:

```clojure
(def backtick-md
  (str/join "\n" ["# Example"
                  ""
                  (str (char 96) (char 96) (char 96) "clojure")
                  "(+ 1 2)"
                  (str (char 96) (char 96) (char 96))
                  ""
                  "Some text."
                  ""
                  (str (char 96) (char 96) (char 96) "clojure")
                  "(* 3 4)"
                  (str (char 96) (char 96) (char 96))]))

(count (parser/parse-code-blocks backtick-md))
```

```clojure
(->> (parser/parse-code-blocks backtick-md)
     (mapv :code)
     (mapv str/trim))
```

### Formatting Output

`format-output` converts an evaluation result map into the display format used in snapshots:

```clojure
(parser/format-output {:result 42 :stdout "" :error nil})
```

With stdout captured:

```clojure
(parser/format-output {:result :done :stdout "Hello, World!" :error nil})
```

With an error:

```clojure
(parser/format-output {:result nil :stdout "" :error "Division by zero"})
```

With nil result (no output shown for nil):

```clojure
(parser/format-output {:result nil :stdout "" :error nil})
```

## Evaluation Functions

The `clj-transcript.eval` namespace handles code execution with stdout capture.

```clojure
(require '[clj-transcript.eval :as eval])
```

### Evaluating Code Blocks

`eval-block` evaluates a string of Clojure code and returns a map with the result, captured stdout, and any error.

Basic evaluation:

```clojure
(eval/eval-block "(+ 1 2 3)" (atom {}))
```

Multiple expressions - returns the last result:

```clojure
(eval/eval-block "(do (def a 10) (def b 20) (+ a b))" (atom {}))
```

Capturing stdout:

```clojure
(let [result (eval/eval-block "(do (println \"Hello!\") (println \"World!\") :done)" (atom {}))]
  {:result (:result result)
   :stdout-lines (str/split-lines (:stdout result))
   :error (:error result)})
```

Error handling - errors are captured, not thrown:

```clojure
(let [result (eval/eval-block "(/ 1 0)" (atom {}))]
  {:has-error (some? (:error result))
   :error-contains-divide (str/includes? (or (:error result) "") "Divide")})
```

### Shared Context Across Blocks

The context atom maintains state across multiple `eval-block` calls. This is how definitions from earlier blocks can be used in later ones:

```clojure
(let [ctx (atom {})]
  ;; First block defines a function
  (eval/eval-block "(defn square [x] (* x x))" ctx)
  ;; Second block uses that function
  (:result (eval/eval-block "(square 7)" ctx)))
```

## Core Functions

The `clj-transcript.core` namespace provides the main API for running transcripts.

```clojure
(require '[clj-transcript.core :as core])
```

### Block Comparison

`compare-blocks` compares executed blocks against snapshot blocks and categorizes them into:
- `:unchanged` - code matches and output matches
- `:failed` - code matches but output differs
- `:new` - block exists in source but not in snapshot
- `:removed` - block exists in snapshot but not in source

Identical blocks are unchanged:

```clojure
(let [src [{:code "(+ 1 2)" :output-str ";;=> 3"}]
      snap [{:code "(+ 1 2)" :output ";;=> 3"}]
      result (core/compare-blocks src snap)]
  {:unchanged (count (:unchanged result))
   :failed (count (:failed result))
   :new (count (:new result))
   :removed (count (:removed result))})
```

Output mismatch is a failure:

```clojure
(let [src [{:code "(+ 1 2)" :output-str ";;=> 3"}]
      snap [{:code "(+ 1 2)" :output ";;=> 999"}]
      result (core/compare-blocks src snap)]
  {:status (if (seq (:failed result)) :fail :pass)
   :expected (get-in (first (:failed result)) [:expected-output])
   :actual (get-in (first (:failed result)) [:actual-output])})
```

New block added:

```clojure
(let [src [{:code "(+ 1 2)" :output-str ";;=> 3"}
           {:code "(* 3 4)" :output-str ";;=> 12"}]
      snap [{:code "(+ 1 2)" :output ";;=> 3"}]
      result (core/compare-blocks src snap)]
  {:unchanged (count (:unchanged result))
   :new (count (:new result))
   :new-code (:code (first (:new result)))})
```

Block removed:

```clojure
(let [src [{:code "(+ 1 2)" :output-str ";;=> 3"}]
      snap [{:code "(+ 1 2)" :output ";;=> 3"}
            {:code "(* 3 4)" :output ";;=> 12"}]
      result (core/compare-blocks src snap)]
  {:unchanged (count (:unchanged result))
   :removed (count (:removed result))
   :removed-code (:code (first (:removed result)))})
```

Modified block shows as both new and removed:

```clojure
(let [src [{:code "(+ 1 2 3)" :output-str ";;=> 6"}]
      snap [{:code "(+ 1 2)" :output ";;=> 3"}]
      result (core/compare-blocks src snap)]
  {:new (count (:new result))
   :removed (count (:removed result))
   :new-code (:code (first (:new result)))
   :removed-code (:code (first (:removed result)))})
```

### Executing Blocks

`execute-blocks` runs a sequence of code blocks and returns them with their outputs:

```clojure
(let [blocks [{:code "(+ 1 2)" :start 0 :end 10}
              {:code "(* 3 4)" :start 20 :end 30}]]
  (->> (core/execute-blocks blocks)
       (mapv #(select-keys % [:code :output-str]))))
```

### Snapshot Path

`snapshot-path` returns the path where a transcript's snapshot is stored:

```clojure
(.getName (core/snapshot-path "transcripts/example.md"))
```

```clojure
(.getName (core/snapshot-path "path/to/deep/nested/file.md"))
```

## Summary

The clj-transcript library provides:

| Namespace | Purpose |
|-----------|---------|
| `parser`  | Extract code blocks from markdown, pair with outputs |
| `eval`    | Execute Clojure code with stdout capture |
| `core`    | Compare blocks, detect changes, manage snapshots |

These building blocks enable transcript-based testing where markdown files serve as both documentation and executable test suites.
