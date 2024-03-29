# iwrotesomecode/swimwild

Migration of https://swim.josephdumont.com to Clojure/script, from SQLite to PostgreSQL/PostGIS, incorporating precipitation data and analysis of time-series and spatial correlations.

`src/iwrotesomecode/dataingest.clj` is where most functionality is fleshed out. It downloads and processes data from California State data repositories and is meant to be a daily update. The current iteration from the website runs on a cron job. It currently updates a SQLite database. 

TODO: Precipitation data not yet live, conversion from node/express to a Clojure backend is in its first steps, not yet begun moving from raw leaflet to react-leaflet and helix (or reagent, uix). 

![Screenshot of app featuring Bay Area with water sampling locations](swim.png)
<!-- ## Installation -->

<!-- Download from https://github.com/iwrotesomecode/swimwild -->

<!-- ## Usage -->

<!-- FIXME: explanation -->

<!-- Run the project directly, via `:exec-fn`: -->

<!--     $ clojure -X:run-x -->
<!--     Hello, Clojure! -->

<!-- Run the project, overriding the name to be greeted: -->

<!--     $ clojure -X:run-x :name '"Someone"' -->
<!--     Hello, Someone! -->

<!-- Run the project directly, via `:main-opts` (`-m iwrotesomecode.swimwild`): -->

<!--     $ clojure -M:run-m -->
<!--     Hello, World! -->

<!-- Run the project, overriding the name to be greeted: -->

<!--     $ clojure -M:run-m Via-Main -->
<!--     Hello, Via-Main! -->

<!-- Run the project's tests (they'll fail until you edit them): -->

<!--     $ clojure -T:build test -->

<!-- Run the project's CI pipeline and build an uberjar (this will fail until you edit the tests to pass): -->

<!--     $ clojure -T:build ci -->

<!-- This will produce an updated `pom.xml` file with synchronized dependencies inside the `META-INF` -->
<!-- directory inside `target/classes` and the uberjar in `target`. You can update the version (and SCM tag) -->
<!-- information in generated `pom.xml` by updating `build.clj`. -->

<!-- If you don't want the `pom.xml` file in your project, you can remove it. The `ci` task will -->
<!-- still generate a minimal `pom.xml` as part of the `uber` task, unless you remove `version` -->
<!-- from `build.clj`. -->

<!-- Run that uberjar: -->

<!--     $ java -jar target/swimwild-0.1.0-SNAPSHOT.jar -->

<!-- If you remove `version` from `build.clj`, the uberjar will become `target/swimwild-standalone.jar`. -->

<!-- ## Options -->

<!-- FIXME: listing of options this app accepts. -->

<!-- ## Examples -->

<!-- ... -->

<!-- ### Bugs -->

<!-- ... -->

<!-- ### Any Other Sections -->
<!-- ### That You Think -->
<!-- ### Might be Useful -->

<!-- ## License -->

Copyright © 2022 JJ
Distributed under the Eclipse Public License version 1.0.
