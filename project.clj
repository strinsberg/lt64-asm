(defproject lt64-asm "0.0.3-SNAPSHOT"
  :description "Assembler for the lieutenant-64 virtual machine"
  :url "https://github.com/strinsberg/lt64-asm"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/tools.cli "1.0.206"]]
  :main ^:skip-aot lt64-asm.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
