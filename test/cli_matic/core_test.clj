(ns cli-matic.core-test
  (:require
   [cli-matic.core :as sut]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]))

(defn- submap? [m1 m2]
  (= m1 (select-keys m2 (keys m1))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def basic-cli-spec
  {:summary "Write arguments to the standard output"
   :usage   "[-n] [<string> ...]"
   :options [["-n" nil "Do not print the trailing newline character."
              :id :skip-newline]]})

(def subcommand-cli-spec
  {:summary     "The stupid content tracker" ; Now that's a name no one would self-apply where I come from.
   :usage       "[<options>] <command>"
   :options     [[nil  "--git-dir <path>" "Set the path to the repository (\".git\" directory)"]
                 ["-v" "--version"]]
   :subcommands {"commit"
                 {:summary "Record changes to the repository"
                  :usage   "[<options>] [--] [<pathspec> ...]"
                  :options [[nil  "--author <author>" "Override the commit author"]
                            ["-p" "--patch"           "Use interactive patch selection interface"]]}}})

(def nested-cli-spec
  {:summary     "Unified tool to manage your AWS services"
   :usage       "[<options>] <command>"
   :options     [[nil "--debug"]
                 [nil "--profile <profile>"]]
   :subcommands {"iam"
                 {:summary "Identity and Access Management (IAM) commands"
                  :usage   ""
                  :subcommands {"add-role-to-instance-profile"
                                {:summary "Adds the specified IAM role to the specified instance profile"
                                 :usage   "<options>"
                                 :options [[nil "--instance-profile-name <value>"]
                                           [nil "--role-name <value>"]
                                           [nil "--cli-input-json <json-str>"]
                                           [nil "--cli-input-yaml <yaml-str>"]
                                           [nil "--generate-cli-skeleton <value>"]]}}}}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest get-command
  (is (= (sut/get-command nested-cli-spec [])
         nested-cli-spec)
      "Return top-level command when passed `[]`")
  (is (= (-> nested-cli-spec
             :subcommands
             (get "iam")
             :subcommands
             (get "add-role-to-instance-profile"))
         (sut/get-command nested-cli-spec ["iam" "add-role-to-instance-profile"]))
      "Returns nested command when passed a path to a command"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- va
  "Helper to call `validate-args` with string split into arguments.

  Not smart enough to handle quoted arguments, so keep it simple."
  [cli-spec arg-str]
  (sut/validate-args
   (if (= "" arg-str) ; str/split annoyingly returns [""]
     []
     (str/split arg-str #" "))
   (:options cli-spec)
   cli-spec))

(deftest basic-cli
  (testing "`[]` is returned as `:command`"
    (let [ret (va basic-cli-spec "")]
      (is (submap? {:command   []
                    :options   {}
                    :arguments []}
                   ret))))
  (testing "options are validated"
    (let [ret (va basic-cli-spec "-x")]
      (is (str/includes? (:exit-message ret) "Unknown option:"))))
  (testing "can have arguments and options"
    (let [ret (va basic-cli-spec "-n file1 file2")]
      (is (submap? {:command   []
                    :options   {:skip-newline true}
                    :arguments ["file1" "file2"]}
                   ret)))))

(deftest subcommand-cli
  (testing "root command"
    (testing "`[]` is returned as `:command`"
      (let [ret (va subcommand-cli-spec "")]
        (is (submap? {:command   []
                      :options   {}
                      :arguments []}
                     ret))))
    (testing "options are validated"
      (let [ret (va subcommand-cli-spec "-x")]
        (is (str/includes? (:exit-message ret) "Unknown option:")))))
  (testing "subcommand"
    (testing "must exist"
      (let [ret (va subcommand-cli-spec "bake-cake")]
        (is (str/includes? (:exit-message ret) "Unknown command:"))))
    (testing "options are validated"
      (let [ret (va subcommand-cli-spec "commit -x")]
        (is (str/includes? (:exit-message ret) "Unknown option:"))))
    (testing "is returned as `:command`"
      (let [ret (va subcommand-cli-spec "commit")]
        (is (submap? {:command ["commit"]} ret))))
    (testing "can have arguments and options"
      (let [ret (va subcommand-cli-spec "commit --patch --author cameron@collbox.co core.clj")]
        (is (submap? {:command   ["commit"]
                      :options   {:author "cameron@collbox.co"
                                  :patch  true}
                      :arguments ["core.clj"]}
                     ret))))
    (testing "inherits higher-level options"
      (let [ret (va subcommand-cli-spec "--git-dir=/other/proj/.git commit --patch core.clj")]
        (is (submap? {:command   ["commit"]
                      :options   {:git-dir "/other/proj/.git"
                                  :patch   true}
                      :arguments ["core.clj"]}
                     ret))))))

(deftest nested-cli
  (testing "returns nested command and all layers of arguments merged"
    (let [ret (va nested-cli-spec "--profile collbox --debug iam add-role-to-instance-profile foo")]
      (is (submap? {:command   ["iam" "add-role-to-instance-profile"]
                    :options   {:debug   true
                                :profile "collbox"}
                    :arguments ["foo"]} ret))))
  (testing "parent commands can be run directly"
    (let [ret (va nested-cli-spec "iam")]
      (is (submap? {:command ["iam"]} ret))))
  (testing "command must exist and error reflects depth of issue"
    (let [ret (va nested-cli-spec "iam missing")]
      (is (str/includes? (:exit-message ret) "Unknown command: 'iam missing'")))
    (let [ret (va nested-cli-spec "you are missing")]
      (is (str/includes? (:exit-message ret) "Unknown command: 'you'")))))
