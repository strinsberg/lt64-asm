(defproject lt64-asm "0.0.3-2"
  :description "Assembler for the lieutenant-64 virtual machine"
  :url "https://github.com/strinsberg/lt64-asm"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/tools.cli "1.0.206"]]
  :main ^:skip-aot lt64-asm.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
