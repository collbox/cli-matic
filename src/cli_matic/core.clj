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

;; I'd love to have this be a one-liner that could take `cli-spec`,
;; but having a map under :commands with just `nil` mapped to the
;; commands sounds stupid.
;;
;; TODO: revisit once we do multi-level nesting.
(defn get-command [cli-spec command-name]
  (if command-name
    (get-in cli-spec [:subcommands command-name])
    cli-spec))

;; Options summary is from `clojure.tools.cli`.  Could rework this and
;; build it directly from `commands` (using functionality in
;; `clojure.tools.cli`), I think.  That would be helpful for
;; generating docs and such.  It's silly to process empty argument
;; lists just to get the usage details.
(defn usage [cli-spec command-name options-summary]
  ;; TODO: If we're going to show any notion of parent command
  ;; arguments (open question) then we need the path to the command,
  ;; not just the command name.
  (let [{:keys [options subcommands summary usage]}
        (-> (:subcommands cli-spec)
            (get-command command-name))]
    (->> [summary
          ""
          (str "Usage: "
               (:base-command cli-spec "command")
               (when command-name
                 (str " " command-name))
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
  options provided."
  [raw-args option-defs cli-spec & [base]]
  (let [{:keys [command-name options arguments errors summary]}
        (-> (cli/parse-opts raw-args option-defs :in-order true)
            (deep-merge base))
        command (get-command cli-spec command-name)]
    (cond
      errors                             {:exit-message (error-msg errors)}
      (and (seq arguments)
           (seq (:subcommands command))) (let [[sub-command-name & arguments] arguments]
                                           (if-let [sub-command (get-command cli-spec sub-command-name)]
                                             (validate-args arguments
                                                            (:options sub-command)
                                                            cli-spec
                                                            {:command-name sub-command-name
                                                             :options      options})
                                             {:exit-message (error-msg [(format "Unknown command: '%s'" sub-command-name)])}))
      ;; I think this is in the right place.  We'll parse down to the
      ;; deepest command before invoking help, meaning we'll show it
      ;; for the right command (not the parent command).
      (:help options)                    {:exit-message (usage summary cli-spec command-name) :ok? true}
      command                            {:command command-name :options options :arguments arguments}
      ;; TODO: is this ever used now?
      :else                              {:exit-message (usage summary cli-spec nil)})))
