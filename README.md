# clj-transcript

Transcript-based testing for Clojure.

A transcript is a markdown file with embedded Clojure code blocks. When executed, each code block is evaluated and its output (stdout + result) is appended below the block. The resulting markdown is compared against a saved snapshot to detect regressions.

## Installation

This project uses [devenv](https://devenv.sh/) for development environment management.

```bash
# Enter the development environment
devenv shell
```

## Usage

### CLI

```bash
# Run all transcript tests
transcript run

# Run a specific transcript
transcript run transcripts/example.md

# Accept output for a specific transcript (creates/updates snapshot)
transcript accept transcripts/example.md

# Accept all transcript outputs
transcript accept-all
```

### Programmatic API

```clojure
(require '[clj-transcript.core :as transcript])

;; Run a single transcript and compare against snapshot
(transcript/run-transcript "transcripts/example.md")
;; => {:path "transcripts/example.md"
;;     :status :pass  ; or :fail, :new
;;     :diff nil      ; diff info if failed
;;     :result "..."}

;; Accept a transcript (save current output as snapshot)
(transcript/accept-transcript! "transcripts/example.md")

;; Run all transcripts in a directory
(transcript/run-all "transcripts")

;; Accept all transcripts
(transcript/accept-all! "transcripts")
```

## How It Works

1. **Write a transcript**: Create a markdown file with Clojure code blocks:

   ````markdown
   # My Feature

   This demonstrates my feature.

   ```clojure
   (+ 1 2 3)
   ```

   ```clojure
   (println "Hello!")
   :done
   ```
   ````

2. **Accept the output**: Run `transcript accept transcripts/my-feature.md` to execute the code and save the output as a snapshot in `.clj-transcript/my-feature.md`.

3. **Run tests**: Use `transcript run` to re-execute all transcripts and compare against saved snapshots. If the output changes, the test fails.

## Output Format

When a transcript is executed, output blocks are appended after each code block:

````markdown
```clojure
(+ 1 2 3)
```

```
;;=> 6
```

```clojure
(println "Hello!")
:done
```

```
;; stdout:
Hello!
;;=> :done
```
````

## Directory Structure

```
your-project/
  transcripts/          # Your transcript files
    example.md
    feature-x.md
  .clj-transcript/      # Saved snapshots (git tracked)
    example.md
    feature-x.md
```

## Development

```bash
# Start a REPL
clj -M:repl

# Run unit tests
clj -X:test
```

## Why Transcript Testing?

- **Documentation that stays accurate**: Transcripts serve as living documentation that is verified with every test run.
- **Easy to write**: Just write markdown with code examples.
- **Easy to review**: Snapshot diffs show exactly what changed in the output.
- **Catches regressions**: If behavior changes, the test fails.

## License

MIT
