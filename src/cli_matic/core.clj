(ns cli-matic.core
  "Data-driven argument parsing for complex CLIs."
  (:require [clojure.pprint :as pprint]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]))

;; We'll want to show help for the super command, so make sure we can
;; access its details.
;;
;; TODO: Help output is garbage right now.
(def magic-commands
  {::help {:summary "Show help for command"}})

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

;; TODO:
;; Multiple option entries can share the same :id in order to
;; transform a value in different ways, but only one of these option
;; entries may contain a :default(-fn) entry.
;;
;; This is how they check for duplicate options:
;;   Assert failed: (distinct?* (remove nil? (map :long-opt %)))
;;
;; ID doesn't really do it...it could lead to different arguments
;; parsing to the same ID.  Is that ok?  It seems weird.
;;
;;  (merge-options [["-h" "--help" nil :id :help]]
;;                 [["-v" "--very-helpful"]])
(defn- option-id
  "Return ID of option from option spec."
  [[_short-opt _long-opt _desc & _rst :as opt]]
  #_(or (->> rst (apply hash-map) :id)
      (when long-opt
        (rest (re-find #"^(--[^ =]+)(?:[ =](.*))?" long-opt)))
      ;; More to do here, use their implementation for now.
      (throw
       (ex-info "Option does not have an ID" {:opt opt})))
  (or (:id (#'clojure.tools.cli/compile-spec opt))
      (throw
       (ex-info "Option does not have an ID" {:opt opt}))))

(defn- find-options
  "Find options by ID."
  [options ids]
  (filter (comp (set ids) option-id) options))

(defn- merge-options
  "Merge two option lists, prefering options in opts2."
  [opts1 opts2]
  (cond
    (not (seq opts1)) opts2
    (not (seq opts2)) opts1
    :else
    (->> (concat opts1 opts2)
         (map (juxt option-id identity))
         (into {})
         vals)))

(defn- inherit-options [command-spec inherited-options]
  (if (map? command-spec)
    (update command-spec :options merge-options inherited-options)
    command-spec))

(defn- inherit-subcommands [command-spec inherited-subcommands]
  (if (map? command-spec)
    (update command-spec :subcommands (partial merge inherited-subcommands))
    command-spec))

(defn- get-command
  "Return command-spec of `command`, merging in any inherited options as
  we traverse the tree of subcommands."
  [{:keys [inherited-options inherited-subcommands options subcommands] :as command-spec} command]
  (if (and command-spec (seq command))
    (recur (some-> command-spec
                   :subcommands
                   (get (first command))
                   (inherit-options (find-options options inherited-options))
                   (inherit-subcommands (select-keys subcommands inherited-subcommands)))
           (rest command))
    command-spec))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

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
   (when-let [{:keys [options subcommands summary usage] :as command-spec}
              (get-command cli-spec command)]
     (prn command-spec)
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

(defn validate-args
  "Validate command line arguments.  Either return a map indicating the
  program should exit (with an error message, and optional ok status),
  or a map indicating the action the program should take and the
  options / arguments provided."
  [raw-args cli-spec & {:keys [command] :or {command []} :as base}]
  (if-let [command-spec (get-command cli-spec command)]
    (if (magic-commands command-spec)
      {:exit-message (usage cli-spec command) :ok? true}
      (let [{:keys [options arguments errors summary]}
            (-> (cli/parse-opts raw-args (:options command-spec) :in-order true)
                (deep-merge base))]
        (cond
          errors           {:exit-message (error-msg errors)}
          (and (seq (:subcommands command-spec))
               (seq arguments))
          ,                (recur (rest arguments)
                                  cli-spec
                                  {:command (conj command (first arguments))
                                   :options options})
          (::help options) {:exit-message (usage cli-spec command summary) :ok? true}
          :else            {:command command :options options :arguments arguments})))
    {:exit-message (unknown-command-msg cli-spec command)}))
