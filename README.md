# cli-matic

[![ci][]](https://dl.circleci.com/status-badge/redirect/gh/collbox/cli-matic/tree/develop)

Data-driven argument parsing library for complex Clojure CLIs.

## Introduction

cli-matic builds on [clojure.tools.cli][] to take the grunt work out
of building complex CLIs with nested subcommands, like you'll find in
[git][] and [aws-cli][].

## Dependency Information

*Coming soon.  This project is very new and the API could still
change.*

## Usage

cli-matic parses CLI arguments according to a "cli-spec" map, which
should be somewhat familiar to anyone who has used
[clojure.tools.cli][]:

```clj
(def git-cli-spec
  {:summary      "The stupid content tracker"
   :base-command "git"
   :usage        "[<options>] <command>"
   :options      [[nil  "--git-dir <path>" "Set the path to the repository (\".git\" directory)"]
                  ["-v" "--version"]]
   :subcommands  {"commit"
                  {:summary "Record changes to the repository"
                   :usage   "[<options>] [--] [<pathspec> ...]"
                   :options [[nil  "--author <author>" "Override the commit author"]
                             ["-p" "--patch"           "Use interactive patch selection interface"]]}}})
```

With this map in place, we can now parse arguments (as they would be
passed to a `-main` function):

```clj
(require '[cli-matic.core :as cli])

;; $ git
(cli/validate-args [] git-cli-spec)
;; => {:command [], :options {}, :arguments []}

;; $ git commit
(cli/validate-args ["commit"] git-cli-spec)
;; => {:command ["commit"], :options {}, :arguments []}

;; $ git commit -p --author cameron@collbox.co
(cli/validate-args ["commit" "-p" "--author" "cameron@collbox.co"] git-cli-spec)
;; => {:command ["commit"], :options {:patch true, :author "cameron@collbox.co"}, :arguments []}

;; $ git lost
(cli/validate-args ["lost"] git-cli-spec)
;; => {:exit-message "The following errors occurred while parsing your command:\n\nUnknown command: 'lost'"}
```

n.b. the command your program should subsequently invoke is denoted by
a map of strings, with the root command specified by `[]`.  This
allows us to nest commands arbitrarily deeply, representing something
like [aws-cli][]'s `aws iam add-role-to-instance-profile` with `["iam"
"add-role-to-instance-profile"]`.

After checking for errors (via `:exit-message`), you can then dispatch
based on the returned `:command` value via `case`, `defmulti`, or any
other means.

Sequential argument validation is left to the caller to allow full
flexibility.

## CLI Documentation

The `:summary`, `:base-command`, and `:usage` entries are used solely
for documentation.

## Testing

To run the test suite, run:

```sh
clojure -X:test
```

Use the `try` alias to try out cli-matic's parsing from the
command-line against a sample specification.

```sh
clojure -M:try your --commands here
```

## License

Copyright © CollBox Inc., 2022

Distributed under the MIT License.

[aws-cli]: https://aws.amazon.com/cli/
[ci]: https://dl.circleci.com/status-badge/img/gh/collbox/cli-matic/tree/develop.svg?style=svg
[clojure.tools.cli]: https://github.com/clojure/tools.cli
[git]: https://git-scm.com/
