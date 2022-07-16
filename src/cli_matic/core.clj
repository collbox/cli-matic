(ns cli-matic.core
  "Data-driven argument parsing for complex CLIs."
  (:require [clojure.pprint :as pprint]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]))

(defn- deep-merge
  "Recursively merges maps."
  [& maps]
  (letfn [(m [& xs]
            (if (some #(and (map? %) (not (record? %))) xs)
              (apply merge-with m xs)
              (last xs)))]
    (reduce m maps)))

(defn get-command [command-spec command-path]
  (if (and command-spec (seq command-path))
    (recur (get-in command-spec [:subcommands (first command-path)])
           (rest command-path))
    command-spec))

;; TODO: Rework this to make `option-summary` optional, and build it
;; directly from `cli-spec` (using functionality in
;; `clojure.tools.cli`, as needed).  Would be helpful for generating
;; docs.  It's silly to process empty argument lists just to get usage
;; instructions.
(defn usage
  "Return usage instruction string for command at the path
  `command-path` in `cli-spec` specification.

  `options-summary` is the summary returned by
  `clojure.tools.cli/parse-opts` for the command."
  [cli-spec command-path options-summary]
  (when-let [{:keys [options subcommands summary usage]}
             (get-command cli-spec command-path)]
    (->> [summary
          ""
          (str "Usage: "
               (:base-command cli-spec "command")
               (when (seq command-path)
                 (->> command-path
                      (str/join " ")
                      (str " ")))
               " " usage)
          (when (seq options)
            (str "\nOptions:\n" options-summary))
          (when (seq subcommands)
            (->> subcommands
                 (map (fn [[nm sub]] [nm (:summary sub)]))
                 (sort)
                 (pprint/cl-format nil "~%Commands:~%~:{  ~A~20T~A~:^~%~}")))]
         (remove nil?)
         (str/join \newline))))

(defn- error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn validate-args
  "Validate command line arguments.  Either return a map indicating the
  program should exit (with an error message, and optional ok status),
  or a map indicating the action the program should take and the
  options / arguments provided."
  [raw-args option-defs cli-spec & [base]]
  (let [{:keys [command-path options arguments errors summary]
         :or   {command-path []}}
        (-> (cli/parse-opts raw-args option-defs :in-order true)
            (deep-merge base))
        command (get-command cli-spec command-path)]
    (cond
      errors                             {:exit-message (error-msg errors)}
      (and (seq arguments)
           (seq (:subcommands command))) (let [[sub-command-name & arguments] arguments
                                               sub-command-path               (conj command-path sub-command-name)]
                                           (if-let [sub-command (get-command cli-spec sub-command-path)]
                                             (validate-args arguments
                                                            (:options sub-command)
                                                            cli-spec
                                                            {:command-path sub-command-path
                                                             :options      options})
                                             {:exit-message (error-msg [(format "Unknown command: '%s'"
                                                                                (str/join " " sub-command-path))])}))
      ;; I think this is in the right place.  We'll parse down to the
      ;; deepest command before invoking help, meaning we'll show it
      ;; for the right command (not the parent command).
      (:help options)                    {:exit-message (usage cli-spec command-path summary) :ok? true}
      :else                              {:command command-path :options options :arguments arguments})))
