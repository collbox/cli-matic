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

(defn- commands-summary [commands]
  (->> commands
       (map (fn [[nm sub]] [nm (:summary sub)]))
       (sort)
       (pprint/cl-format nil "Commands:~%~:{  ~A~20T~A~:^~%~}")))

(defn get-command [command-spec command]
  (if (and command-spec (seq command))
    (recur (get-in command-spec [:subcommands (first command)])
           (rest command))
    command-spec))

(defn usage
  "Return usage instruction string for command at the path `command` in
  `cli-spec` specification.

  `options-summary` is the summary returned by `cli/parse-opts` for
  the command."
  ([cli-spec command]
   (let [command-spec      (get-command cli-spec command)
         {:keys [summary]} (cli/parse-opts command (:options command-spec))]
     (usage cli-spec command summary)))
  ([cli-spec command options-summary]
   (when-let [{:keys [options subcommands summary usage]}
              (get-command cli-spec command)]
     (->> [summary
           ""
           (str "Usage: "
                (:base-command cli-spec "command")
                (when (seq command)
                  (->> command
                       (str/join " ")
                       (str " ")))
                " " usage)
           (when (seq options)
             (str "\nOptions:\n" options-summary))
           (when (seq subcommands)
            (str "\n" (commands-summary subcommands)))]
          (remove nil?)
          (str/join \newline)))))

(defn- error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn- unknown-command-msg [cli-spec command]
  (let [command-name          (str/join " " command)
        {:keys [subcommands]} (get-command cli-spec (butlast command))
        msg                   (str (format "Unknown command: '%s'" command-name)
                                   (when (seq subcommands)
                                     (str "\n\n"
                                          (commands-summary subcommands))))]
    (error-msg [msg])))

(defn validate-args
  "Validate command line arguments.  Either return a map indicating the
  program should exit (with an error message, and optional ok status),
  or a map indicating the action the program should take and the
  options / arguments provided."
  [raw-args cli-spec & {:keys [command] :or {command []} :as base}]
  (if-let [command-spec (get-command cli-spec command)]
    (let [{:keys [options arguments errors summary]}
          (-> (cli/parse-opts raw-args (:options command-spec) :in-order true)
              (deep-merge base))]
      (cond
        errors          {:exit-message (error-msg errors)}
        (and (seq (:subcommands command-spec))
             (seq arguments))
        ,               (recur (rest arguments)
                               cli-spec
                               {:command (conj command (first arguments))
                                :options options})
        (:help options) {:exit-message (usage cli-spec command summary) :ok? true}
        :else           {:command command :options options :arguments arguments}))
    {:exit-message (unknown-command-msg cli-spec command)}))
