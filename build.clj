(ns build
  (:require [clojure.tools.build.api :as b]
            [clojure.string :as str]))

(def lib 'io.github.sonntag/clj-transcript)
(def version (format "0.1.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" (name lib) version))

;; Basis for compilation
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [_]
  (clean nil)
  (println "Building" jar-file)
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis @basis
                :src-dirs ["src"]
                :scm {:url "https://github.com/sonntag/clj-transcript"
                      :connection "scm:git:git://github.com/sonntag/clj-transcript.git"
                      :developerConnection "scm:git:ssh://git@github.com/sonntag/clj-transcript.git"
                      :tag (str "v" version)}
                :pom-data [[:description "Transcript-based testing for Clojure"]
                           [:url "https://github.com/sonntag/clj-transcript"]
                           [:licenses
                            [:license
                             [:name "MIT License"]
                             [:url "https://opensource.org/licenses/MIT"]]]]})
  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file})
  (println "Created" jar-file))

(defn install [_]
  (jar nil)
  (println "Installing to local Maven repository...")
  (b/install {:basis @basis
              :lib lib
              :version version
              :jar-file jar-file
              :class-dir class-dir})
  (println "Installed" lib version))

(defn deploy [_]
  (jar nil)
  (println "Deploying to Clojars...")
  ((requiring-resolve 'deps-deploy.deps-deploy/deploy)
   {:installer :remote
    :artifact jar-file
    :pom-file (str class-dir "/META-INF/maven/"
                   (namespace lib) "/"
                   (name lib) "/pom.xml")})
  (println "Deployed" lib version "to Clojars"))
