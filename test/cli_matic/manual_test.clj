(ns cli-matic.manual-test
  (:require [cli-matic.core :as cli-matic]
            [clojure.pprint :as pprint]))

(def cli-spec
  {:summary               "Unified tool to manage your AWS services"
   :base-command          "clojure -M:try"
   :usage                 "[<options>] <command>"
   :options               [[nil "--debug"]
                           [nil "--profile <profile>"]
                           ["-h" "--help"]]
   :inherited-subcommands ["help"]
   :subcommands           {"help" :cli-matic.core/help
                           "iam"
                           {:summary     "Identity and Access Management (IAM) commands"
                            :usage       ""
                            :subcommands {"add-role-to-instance-profile"
                                          {:summary "Adds the specified IAM role to the specified instance profile"
                                           :usage   "<options>"
                                           :options [[nil "--instance-profile-name <value>"]
                                                     [nil "--role-name <value>"]
                                                     [nil "--cli-input-json <json-str>"]
                                                     [nil "--cli-input-yaml <yaml-str>"]
                                                     [nil "--generate-cli-skeleton <value>"]]}}}}})

(defn- exit [status msg]
  (if (pos? status)
    (binding [*out* *err*]
      (println msg))
    (println msg))
  (System/exit status))

(defn -main [& args]
  (let [{:keys [exit-message ok?] :as ret}
        (cli-matic/validate-args args cli-spec)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (pprint/pprint ret))))
