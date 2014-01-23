/**
 * Copyright (C) 2010 ZeroTurnaround OU
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License v2 as published by
 * the Free Software Foundation, with the additional requirement that
 * ZeroTurnaround OU must be prominently attributed in the program.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You can find a copy of GNU General Public License v2 from
 *   http://www.gnu.org/licenses/gpl-2.0.txt
 */

package com.danhaywood.isis.tool.jrebelplugin;

import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.datanucleus.metadata.MetaDataManager;
import org.zeroturnaround.bundled.javassist.ByteArrayClassPath;
import org.zeroturnaround.bundled.javassist.ClassPath;
import org.zeroturnaround.bundled.javassist.ClassPool;
import org.zeroturnaround.bundled.javassist.CtClass;
import org.zeroturnaround.bundled.javassist.LoaderClassPath;
import org.zeroturnaround.bundled.javassist.NotFoundException;
import org.zeroturnaround.javarebel.ClassBytecodeProcessor;
import org.zeroturnaround.javarebel.ClassEventListener;
import org.zeroturnaround.javarebel.ClassResourceSource;
import org.zeroturnaround.javarebel.Integration;
import org.zeroturnaround.javarebel.IntegrationFactory;
import org.zeroturnaround.javarebel.LoggerFactory;
import org.zeroturnaround.javarebel.Plugin;
import org.zeroturnaround.javarebel.ReloaderFactory;

import org.apache.isis.objectstore.jdo.datanucleus.DataNucleusApplicationComponents;

public class IsisJRebelPlugin implements Plugin {

    private static enum LoggingLevel {
        WARN, INFO, DEBUG,
    }

    private static final String ISIS_JREBEL_PLUGIN = "isis-jrebel-plugin.";
    private static final String D_PACKAGE_PREFIX = ISIS_JREBEL_PLUGIN + "packagePrefix";
    private static final String D_LOGGING_LEVEL = ISIS_JREBEL_PLUGIN + "loggingLevel";

    private final Map<String, byte[]> bytecodeByClassName = new HashMap<String, byte[]>();

    private String packagePrefix;
    private LoggingLevel loggingLevel = LoggingLevel.INFO;

    private boolean metDependencies = false;

    public boolean checkDependencies(ClassLoader classLoader, ClassResourceSource classResourceSource) {

        if (metDependencies) {
            return metDependencies;
        }

        packagePrefix = System.getProperty(D_PACKAGE_PREFIX);
        if (packagePrefix == null) {
            logWarn("*****************************************************************");
            logWarn("*");
            logWarn("* Isis JRebel Plugin is ***DISABLED***");
            logWarn("*");
            logWarn("* specify package prefix through system property, eg:");
            logWarn("*   -Disis-jrebel-plugin.packagePrefix=com.mycompany.myapp");
            logWarn("*");
            logWarn("*****************************************************************");
            return false;
        }

        if (classResourceSource.getClassResource("org.apache.isis.core.runtime.system.context.IsisContext") == null) {
            logDebug("Isis JRebel Plugin ignored, Isis framework classes not found");
            return false;
        }

        logInfo("*****************************************************************");
        logInfo("*");
        logInfo("* Isis JRebel Plugin is ENABLED");
        logInfo("*");
        logInfo("* reloading classes under " + packagePrefix);
        logInfo("*");
        logInfo("*****************************************************************");
        return (metDependencies = true);
    }

    public void preinit() {

        // necessary to do again (as well as in checkDependencies) because
        // JRebel seems to instantiate the plugin twice, once to do the check,
        // second to actually initialize.
        packagePrefix = System.getProperty(D_PACKAGE_PREFIX);
        initLogging();

        Integration i = IntegrationFactory.getInstance();
        ClassLoader cl = IsisJRebelPlugin.class.getClassLoader();

        i.addIntegrationProcessor(cl, newIntegrationProcessor());

        ReloaderFactory.getInstance().addClassLoadListener(newClassLoadListener());
        ReloaderFactory.getInstance().addClassReloadListener(newClassReloadListener());
    }

    private void initLogging() {
        String loggingLevelStr = System.getProperty(D_LOGGING_LEVEL);
        this.loggingLevel = LoggingLevel.INFO;
        if("debug".equalsIgnoreCase(loggingLevelStr)) {
            this.loggingLevel=LoggingLevel.DEBUG;
        }
        if("warn".equalsIgnoreCase(loggingLevelStr)) {
            this.loggingLevel=LoggingLevel.WARN;
        }
    }

    // prevent infinite loop
    boolean processing = false;
    // ensure single-threaded
    private Object threadSafety = new Object();

    private ClassBytecodeProcessor newIntegrationProcessor() {
        return new ClassBytecodeProcessor() {

            public byte[] process(ClassLoader cl, String className, byte[] bytecode) {
                synchronized (threadSafety) {
                    if (processing) {
                        return bytecode;
                    }
                    processing = true;
                    try {
                        return processSafely(cl, className, bytecode);
                    } catch (Throwable e) {
                        e.printStackTrace(System.err);
                        return bytecode;
                    } finally {
                        processing = false;
                    }
                }
            }

            private byte[] processSafely(ClassLoader cl, String className, byte[] bytecode) throws NotFoundException, ClassNotFoundException {
                className = className.replace('/', '.');

                if (!underPackage(className)) {
                    return bytecode;
                }

                logDebug("processing: " + className);
                CtClass ctClass = asCtClass(cl, className, bytecode);

                
                boolean persistenceCapable = isPersistenceCapable(ctClass);
                logDebug("  determining whether bytecode represents a persistence-capable entity...");
                if (!persistenceCapable) {
                    logDebug("    not persistence-capable entity, skipping");
                    return bytecode;
                }

                // figure out if this bytecode has been enhanced
                logDebug("  determining whether bytecode has been enhanced...");
                boolean enhanced = isEnhanced(ctClass);

                
                // enhance ...
                if (!enhanced) {
                    logDebug("    not enhanced");

                    // ignore any unenhanced bytecode, and just use
                    // the previous (enhanced) bytecode previous seen.
                    // we expect the IDE's enhancer plugin to provide a further class load with
                    // new enhanced bytes.
                    //
                    // in testing, found that the call with unenhanced bytes often seemed to happen immediately
                    // that the Eclipse compiler finished its compilation (ie eagerly), and that the follow-up load with
                    // enhanced bytes occurred when the object was interacted with (ie lazily).  So, depending on
                    // user action, there could be several seconds (even minutes) gap between the two calls.
                    logDebug("      using previous (enhanced) bytecode");
                    byte[] previousBytecode = bytecodeByClassName.get(className);

                    if(previousBytecode == null) {
                        logWarn("*****************************************************************");
                        logWarn("*");
                        logWarn("* Loaded an unenhanced class, no enhanced class available");
                        logWarn("*");
                        logWarn("* class: " + className);
                        logWarn("*");
                        logWarn("* Did you run the JDO enhancer?");
                        logWarn("*");
                        logWarn("*****************************************************************");
                    } else {
                        bytecode = previousBytecode;
                    }

                } else {
                    logDebug("    enhanced");

                    // the bytecode we have represents an enhanced class, so cache it 
                    // so can use it in future if this class is ever reloaded in an unenhanced form
                    // (ie the other branch of this if statement)
                    bytecodeByClassName.put(className, bytecode);

                    // throw away existing PMF
                    // it's possible that the user will get an exception due to the mismatch
                    // between the enhanced class and the existing metadata; but this is transient.
                    // The developer should simply ignore and continue
                    logDebug("      forcing recreation of PMF next time");
                    DataNucleusApplicationComponents.markAsStale();
                }

                return bytecode;
            }

        };
    }

    private boolean isPersistenceCapable(CtClass ctClass) throws ClassNotFoundException {
        logDebug("  annotations:");
        Object[] annotations = ctClass.getAnnotations();
        boolean persistenceCapable = false;
        for (Object annotation : annotations) {
            logDebug("  - " + annotation);
            if (annotation.toString().contains("@javax.jdo.annotations.PersistenceCapable")) {
                persistenceCapable = true;
            }
        }
        return persistenceCapable;
    }

    private boolean isEnhanced(CtClass ctClass) throws NotFoundException {
        CtClass[] interfaces = ctClass.getInterfaces();
        boolean enhanced = false;
        logDebug("    implements interfaces:");
        if (interfaces != null) {
            for (CtClass ifc : interfaces) {
                logDebug("    - " + ifc.getName());
                if ("javax.jdo.spi.PersistenceCapable".equals(ifc.getName())) {
                    enhanced = true;
                }
            }
        }
        return enhanced;
    }

    private static CtClass asCtClass(ClassLoader cl, String className, byte[] bytecode) throws NotFoundException {
        ClassPool cp = new ClassPool();
        cp.appendClassPath(new ClassPath() {
            public void close() {
            }
            public URL find(String arg0) {
                return null;
            }
            public InputStream openClassfile(String arg0) throws NotFoundException {
                return null;
            }
        });
        cp.appendClassPath(new ByteArrayClassPath(className, bytecode));
        cp.appendSystemPath();
        cp.appendClassPath(new LoaderClassPath(cl));
        CtClass ctClass = cp.get(className);
        ctClass.defrost();
        return ctClass;
    }
    
    private ClassEventListener newClassLoadListener() {
        return new ClassEventListener() {

            @SuppressWarnings("rawtypes")
            public void onClassEvent(int eventType, Class klass) {
                updateIsisMetadata("loading: ", klass);
            }

            public int priority() {
                return 0;
            }
        };
    }

    private ClassEventListener newClassReloadListener() {
        return new ClassEventListener() {
            @SuppressWarnings("rawtypes")
            public void onClassEvent(int eventType, Class klass) {
                updateIsisMetadata("reloading: ", klass);
            }

            public int priority() {
                return 0;
            }
        };
    }

    @SuppressWarnings("rawtypes")
    private void updateIsisMetadata(String msg, Class klass) {

        final String className = klass.getName();
        if (!underPackage(className)) {
            return;
        }

        logDebug(msg + klass.getName());

        logDebug("  removing Isis metadata: " + className);
        if (org.apache.isis.core.runtime.system.context.IsisContext.exists()) {
            org.apache.isis.core.runtime.system.context.IsisContext.getSpecificationLoader().invalidateCache(klass);
        } else {
            logDebug("    skipping, Isis metamodel not yet available");
        }

    }

    private boolean underPackage(String className) {
        return packagePrefix != null && className.startsWith(packagePrefix);
    }

    // //////////////////////////////////////
    
    private void logDebug(String msg) {
        log(LoggingLevel.DEBUG, msg);
    }

    private void logInfo(String msg) {
        log(LoggingLevel.INFO, msg);
    }
    
    private void logWarn(String msg) {
        log(LoggingLevel.WARN, msg);
    }
    
    private void log(LoggingLevel level, String msg) {
        // warn
        if(level == LoggingLevel.WARN) {
            LoggerFactory.getInstance().log(msg);
            System.err.println(msg);
        }
        if(loggingLevel == LoggingLevel.WARN) {
            return;
        }

        // info
        if(level == LoggingLevel.INFO) {
            LoggerFactory.getInstance().log(msg);
            System.out.println(msg);
        }
        if(loggingLevel == LoggingLevel.INFO) {
            return;
        }

        // debug
        if(level == LoggingLevel.DEBUG) {
            LoggerFactory.getInstance().trace(msg);
        }
        if(loggingLevel == LoggingLevel.DEBUG) {
            return;
        }
    }

    // //////////////////////////////////////
    
    public String getId() {
        return "isis-jrebel-plugin";
    }

    public String getName() {
        return "Isis JRebel Plugin";
    }

    public String getDescription() {
        return "Reload Isis and JDO metadata";
    }

    public String getAuthor() {
        return null;
    }

    public String getWebsite() {
        return null;
    }

    public String getSupportedVersions() {
        return null;
    }

    public String getTestedVersions() {
        return null;
    }
}
