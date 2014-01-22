isis-jrebel-plugin
==================

Apache Isis plugin for JRebel.


### Prereqs

First, install JRebel into Eclipse.

Second, check out the source code for this project, and compile

- nb: against 1.4.0-SNAPSHOT of Isis
- nb: currently against 3.1.12-SNAPSHOT of DN-core

Locate the `danhaywood-isis-jrebel-plugin-1.0.0-SNAPSHOT.jar` JAR file (in `target` folder).


### Configure projects in Eclipse

to configure, *Help>JRebel Config Center*, then select the `dom` project (and probably the `webapp` project too):

![](raw/foo.png)



### Update launch config

Then, the app needs to be updated to launch with the JRebel agent and the Isis-JRebel-plugin.


No changes required to the main tab:

![](raw/foo.png)


The main change is on the JVM arguments section on the arguments tab:

![](raw/foo.png)


To dissect this:

* `${jrebel_args}` is a placeholder for the JRebel agent

* `-Drebel.log` tells JRebel whether to write to its log or not

* `-Drebel.plugins` points to this plugin

* `-Disis-jrebel-plugin.packagePrefix` argument tells this plugin to ignore all packages except that specified; set it to the parent package for all your domain object classes.

Obviously, you should adjust the location of the JAR file, and the package prefix as necessary.

The JRebel tab simply reflects the -D settings on the JVM arguments section (above)

![](raw/foo.png)


Or, if you want, copy one of the example `Xxx-PROTOTYPE-no-fixtures.launch` files (under `ide/eclipse/launch` in the archetypes), and add:

    <stringAttribute 
          key="org.eclipse.jdt.launching.VM_ARGUMENTS" 
          value="${jrebel_args} -Drebel.log=false -Drebel.plugins=c:/github/danhaywood/isis-jrebel-plugin/target/danhaywood-isis-jrebel-plugin-1.0.0-SNAPSHOT.jar -Disis-jrebel-plugin.packagePrefix=dom.simple"/>

(adjusting the location of the JAR file, and the package prefix as necessary)