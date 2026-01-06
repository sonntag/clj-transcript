# Example Transcript

This is an example transcript that demonstrates how clj-transcript works.

## Basic Arithmetic

Let's start with some simple math:

```clojure
(+ 1 2 3)
```

## Defining Functions

We can define functions and use them in subsequent blocks:

```clojure
(defn greet [name]
  (str "Hello, " name "!"))
```

```clojure
(greet "World")
```

## Working with Collections

```clojure
(map inc [1 2 3 4 5])
```

## Capturing Output

Standard output is also captured:

```clojure
(println "This is printed to stdout")
(+ 40 2)
```

## Multiple Expressions

When a block has multiple expressions, only the last result is shown:

```clojure
(def x 10)
(def y 20)
(+ x y)
```
