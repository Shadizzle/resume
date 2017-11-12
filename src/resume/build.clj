(ns resume.build
  (:require [boot.core :as boot]
            [clojure.java.io :as io]
            [clojure.string :refer [split]]
            [markdown.core :refer [md-to-html-string-with-meta]]
            [hiccup.page :refer [html5]]))

;; Transformations (Pure)

(defn get-id [doc]
  (-> doc :metadata :id first))

(defn vec->order [v]
  (zipmap v (range)))

(defn style-tag [path]
  [:link {:rel  "stylesheet"
          :type "text/css"
          :href path}
    nil])

(defn md->div [md]
  [:div {:id (get-id md)} (:html md)])

;; Actions (Impure)

(defn parse-file [file]
  (-> file slurp md-to-html-string-with-meta))

(defn parse-dir [dir opts]
  (->> dir
    (map parse-file)
    (sort-by (:sort opts))
    (map md->div)))

(defn build-doc [in out opts]
  (let [head [:head [:title (:title opts)]
                    (map style-tag (:styles opts))]
        body [:body (parse-dir in {:sort #(get (-> opts :order vec->order) (get-id %))})]
        doc  (html5 {:lang (opts :lang)} head body)]
    (spit out doc)))

(defn copy-file [in out]
  (doto out
    io/make-parents
    (spit (slurp in))))

;; Boot Task

(boot/deftask build
  "Build the entire project document."
  []
  (fn build-middleware [next-handler]
    (fn build-handler [fileset]
      (let [tmp (boot/tmp-dir!)
            in-files (boot/input-files fileset)
            md-files (boot/by-re [#"^markup/en/.+\.md$"] in-files)
            css-files (boot/by-re [#"^styles/.+\.css$"] in-files)
            html-out (io/file tmp "index.html")]
        (build-doc (map boot/tmp-file md-files) html-out
          {:lang "en"
           :title "Resume - Shayden Martin"
           :styles (map :path css-files)
           :order ["contact"
                   "resume"
                   "professional-experience"
                   "educational-background"
                   "references"]})
        ;; NOTE: There has to be a better way to do this css part,
        ;; it should also be it's own task really.
        (doseq [css-file css-files]
          (let [css-out (io/file tmp (boot/tmp-path css-file))]
            (copy-file (boot/tmp-file css-file) css-out)))
        (-> fileset
          (boot/add-resource tmp)
          boot/commit!
          next-handler)))))
