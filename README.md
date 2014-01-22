isis-jrebel-plugin
==================

This is a plugin for [Apache Isis](http://isis.apache.org) plugin for [JRebel](http://zeroturnaround.com/software/jrebel/).  By configuring it you can develop your Isis application without having to restart the application.

This plugin assumes you are using the Isis with the JDO (DataNucleus) objectstore.

## Usage notes


In general you can change any domain object or service.  However, be aware that:

* the enhancement of the domain class is still done by the IDE plugin.

* the changed class is only reloaded when you next interact with the app

* on occasion you may get an exception on the interaction that causes the domain class(es) to reload.  This is caused by a temporary mismatch between the loaded class and the JDO metadata.  Just ignore the exception and carry on.

* new properties of persistent domain objects must be marked as optional (`@Column(allowNulls="true")`
  * it might be possible to specify a JDO [default](http://www.datanucleus.org/products/accessplatform_3_2/jdo/orm/schema_mapping.html#nullsdefaults) [clause](http://www.datanucleus.org/products/accessplatform_3_2/jdo/annotations.html#Column) to circumvent this; not yet tested.


## Prerequisites

* install JRebel into Eclipse.
   * this plugin has not been tested against other IDEs, but might well work...

* grab out the [Isis source code](http://github.com/apache/isis) (`1.4.0-SNAPSHOT`), and compile
   * as described on the [Isis website](http://isis.apache.org/contributors/building-isis.html)

* check out the source code for this project, and compile


Locate the `danhaywood-isis-jrebel-plugin-1.0.0-SNAPSHOT.jar` JAR file (in `target` folder).


## Configure projects in Eclipse

to configure, *Help>JRebel Config Center*, then select the `dom` project (and probably the `webapp` project too):

![](https://raw2.github.com/danhaywood/isis-jrebel-plugin/master/docs/images/eclipse-jrebel-config-center.png)



## Update launch config

Then, the app needs to be updated to launch with the JRebel agent and the Isis-JRebel-plugin.

### Main tab

No changes required to the main tab:

![](https://raw2.github.com/danhaywood/isis-jrebel-plugin/master/docs/images/eclipse-run-config-1.png)


### Arguments tab

The only change required is to the JVM arguments section on the arguments tab:

![](https://raw2.github.com/danhaywood/isis-jrebel-plugin/master/docs/images/eclipse-run-config-2.png)


To dissect this:

* `${jrebel_args}` is a placeholder for the JRebel agent

* `-Drebel.log` tells JRebel whether to write to its log or not

* `-Drebel.plugins` points to this plugin

* `-Disis-jrebel-plugin.packagePrefix` argument tells this plugin to ignore all packages except that specified; set it to the parent package for all your domain object classes.

Obviously, you should adjust the location of the JAR file, and the package prefix as necessary.


### JRebel tab

The JRebel tab simply reflects the -D settings on the JVM arguments section (above)

![](https://raw2.github.com/danhaywood/isis-jrebel-plugin/master/docs/images/eclipse-run-config-3.png)


### Editing the `.launch` config file directly

Alternatively, copy one of the example `Xxx-PROTOTYPE-no-fixtures.launch` files (under `ide/eclipse/launch` in the archetypes), and add:

    <stringAttribute 
          key="org.eclipse.jdt.launching.VM_ARGUMENTS" 
          value="${jrebel_args} -Drebel.log=false -Drebel.plugins=c:/github/danhaywood/isis-jrebel-plugin/target/danhaywood-isis-jrebel-plugin-1.0.0-SNAPSHOT.jar -Disis-jrebel-plugin.packagePrefix=dom.simple"/>

(adjusting the location of the JAR file, and the package prefix as necessary)