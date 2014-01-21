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

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.jdo.JDOEnhancer;
import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.metadata.ClassMetadata;
import javax.jdo.metadata.JDOMetadata;
import javax.jdo.metadata.Metadata;
import javax.jdo.metadata.PackageMetadata;
import javax.jdo.metadata.TypeMetadata;
import javax.jdo.spi.PersistenceCapable;

import org.datanucleus.ClassLoaderResolver;
import org.datanucleus.ClassLoaderResolverImpl;
import org.datanucleus.api.jdo.JDOPersistenceManagerFactory;
import org.datanucleus.api.jdo.metadata.JDOMetaDataManager;
import org.datanucleus.enhancer.DataNucleusEnhancer;
import org.datanucleus.metadata.AbstractClassMetaData;
import org.datanucleus.metadata.MetaDataManager;
import org.zeroturnaround.bundled.javassist.ByteArrayClassPath;
import org.zeroturnaround.bundled.javassist.ClassPool;
import org.zeroturnaround.bundled.javassist.CtClass;
import org.zeroturnaround.bundled.javassist.CtField;
import org.zeroturnaround.bundled.javassist.LoaderClassPath;
import org.zeroturnaround.javarebel.ClassBytecodeProcessor;
import org.zeroturnaround.javarebel.ClassEventListener;
import org.zeroturnaround.javarebel.ClassResourceSource;
import org.zeroturnaround.javarebel.Integration;
import org.zeroturnaround.javarebel.IntegrationFactory;
import org.zeroturnaround.javarebel.LoggerFactory;
import org.zeroturnaround.javarebel.Plugin;
import org.zeroturnaround.javarebel.ReloaderFactory;

import org.apache.isis.core.runtime.persistence.PersistenceSessionFactoryDelegating;
import org.apache.isis.core.runtime.system.persistence.PersistenceSessionFactory;
import org.apache.isis.objectstore.jdo.datanucleus.DataNucleusApplicationComponents;
import org.apache.isis.objectstore.jdo.datanucleus.DataNucleusPersistenceMechanismInstaller;

/**
 * A plugin that supports hotloading of demo-app config.
 */
public class IsisJRebelPlugin implements Plugin {

    private final Set<String> persistenceCapables = new HashSet<String>();

    private final Map<String, byte[]> enhancedBytesByClassName = new HashMap<String, byte[]>();

    public void preinit() {

        Integration i = IntegrationFactory.getInstance();
        ClassLoader cl = IsisJRebelPlugin.class.getClassLoader();

        i.addIntegrationProcessor(cl, newIntegrationProcessor());

        ReloaderFactory.getInstance().addClassLoadListener(newClassLoadListener());
        ReloaderFactory.getInstance().addClassReloadListener(newClassReloadListener());
    }

    private ClassBytecodeProcessor newIntegrationProcessor() {
        return new ClassBytecodeProcessor() {

            public byte[] process(ClassLoader cl, String className, byte[] bytecode) {

                try {
                    className = className.replace('/', '.');

                    if (!className.startsWith("dom.simple")) {
                        return bytecode;
                    }

                    log("processing: " + className);

                    log("  removing Isis metadata: " + className);
                    if (org.apache.isis.core.runtime.system.context.IsisContext.exists()) {
                        org.apache.isis.core.runtime.system.context.IsisContext.getSpecificationLoader().invalidateCacheForJust(className);
                    } else {
                        log("      skipping, Isis metamodel not yet available");
                    }

                    ClassPool cp = new ClassPool();
                    cp.appendClassPath(new RebelClassPath());
                    cp.appendClassPath(new ByteArrayClassPath(className, bytecode));
                    cp.appendSystemPath();
                    cp.appendClassPath(new LoaderClassPath(cl));
                    CtClass ctClass = cp.get(className);
                    ctClass.defrost();

                    log("  annotations:");
                    Object[] annotations = ctClass.getAnnotations();
                    boolean persistenceCapable = false;
                    for (Object annotation : annotations) {
                        log("  - " + annotation);
                        if (annotation.toString().contains("@javax.jdo.annotations.PersistenceCapable")) {
                            persistenceCapable = true;
                        }
                    }

                    if (!persistenceCapable) {
                        log("  not persistence-capable entity, skipping");
                        return bytecode;
                    }


                    // figure out if this bytecode has been enhanced
                    log("  determining whether bytecode has been enhanced...");
                    CtClass[] interfaces = ctClass.getInterfaces();
                    boolean enhanced = false;
                    log("    implements interfaces:");
                    if (interfaces != null) {
                        for (CtClass ifc : interfaces) {
                            log("    - " + ifc.getName());
                            if ("javax.jdo.spi.PersistenceCapable".equals(ifc.getName())) {
                                enhanced = true;
                            }
                        }
                    }

                    if (!enhanced) {
                        log("    not enhanced");
                    } else {
                        log("    enhanced");
                    }


                    log("  loading bytecode into separate classloader ");
                    ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
                    CustomClassLoader customClassLoader = new CustomClassLoader(systemClassLoader);

                    customClassLoader.defineClass(className, bytecode);


                    // debugging; just to show what actually was loaded...
                    Class<?> cls = customClassLoader.loadClass(className);
                    log("    loaded: " + cls.getName());
                    log("    - classloader: " + cls.getClassLoader().toString());
                    if(false) {
                        log("    - methods:");
                        Method[] methods = cls.getMethods();
                        for (Method method : methods) {
                            log("      - " + method.toString());
                        }
                    }

                    // enhance ... 
                    //
                    if (!enhanced) {

                        // however, that I don't the enhancement stuff think works, 
                        // but I don't think is necessary either, because we can rely on 
                        // the Eclipse plugin doing the enhancement instead.
                        //
                        // instead, any unenhanced bytecode is ignored, and just use
                        // the 'best available'

                        if (false) {
                            // don't think this works...
                            log("  performing an in-memory enhancement of the bytecode ");
                            bytecode = enhance(className, bytecode, customClassLoader);

                        } else {
                            // ... but using the previous should.
                            log("  using previous (enhanced) bytecode");
                            bytecode = enhancedBytesByClassName.get(className);
                        }
                    } 
                    


                    // by this point, the bytecode is good
                    enhancedBytesByClassName.put(className, bytecode);

                    log("  updating JDO metadata: " + className);

                    MetaDataManager metaDataManager = DataNucleusApplicationComponents.getMetaDataManager();
                    if (metaDataManager == null) {
                        log("    DataNucleus not yet instantiated so skipping");
                        
                    } else {
                    
                        log("    unloading metadata");
                        metaDataManager.unloadMetaDataForClass(className);
                        
                        
                        log("    creating metadata");
                        
                        ClassLoaderResolver clr = new ClassLoaderResolverImpl();
                        clr.registerUserClassLoader(customClassLoader);
                        AbstractClassMetaData metaDataForClass = metaDataManager.getMetaDataForClass(className, clr);
                        log("      JDO metadata: " + metaDataForClass.toString("", 
                            "        "));
                    }

                    return bytecode;

                } catch (Throwable e) {
                    e.printStackTrace(System.err);
                    return bytecode;
                }
            }

            private byte[] enhance(String className, byte[] bytecode, CustomClassLoader customClassLoader) {
                log("    not enhanced, enhancing");
                Map<String, Object> props = new HashMap<String, Object>();
                props.put("datanucleus.primaryClassLoader", customClassLoader);
                props.put("datanucleus.identifier.case", "PreserveCase");
                props.put("javax.jdo.PersistenceManagerFactoryClass", "org.datanucleus.api.jdo.JDOPersistenceManagerFactory");
                props.put("javax.jdo.option.ConnectionDriverName", "org.hsqldb.jdbcDriver");
                props.put("javax.jdo.option.ConnectionURL", "jdbc:hsqldb:mem:test");
                props.put("javax.jdo.option.ConnectionUserName", "sa");
                props.put("javax.jdo.option.ConnectionPassword", "");

                PersistenceManagerFactory persistenceManagerFactory = JDOHelper.getPersistenceManagerFactory(props, "simple");
                TypeMetadata typeMetadata = persistenceManagerFactory.getMetadata(className);

                log("      typeMetadata: " + typeMetadata.getName());
                Metadata md = typeMetadata;
                Metadata parent;
                JDOMetadata jdoMetadata = null;
                while ((parent = md.getParent()) != null) {
                    log("      - parent: " + parent.getClass().getName());
                    log("      - parent.toString():\n" + parent.toString());
                    md = parent;
                    if (md instanceof JDOMetadata) {
                        jdoMetadata = (JDOMetadata) md;
                    }
                }

                if (jdoMetadata == null) {
                    log("      could not locate parent jdo metadata, skipping");
                    return bytecode;
                }

                // enhance
                JDOEnhancer enhancer = JDOHelper.getEnhancer();
                enhancer.setClassLoader(customClassLoader);

                enhancer.registerMetadata(jdoMetadata);

                // Enhance the in-memory bytes
                enhancer.addClass(className, bytecode);
                enhancer.enhance();
                
                bytecode = enhancer.getEnhancedBytes(className);
                return bytecode;
            }
        };
    }

    private void log(String msg) {
        LoggerFactory.getInstance().log(msg);
        System.err.println(msg);
    }

    private ClassEventListener newClassReloadListener() {
        return new ClassEventListener() {
            @SuppressWarnings("rawtypes")
            public void onClassEvent(int eventType, Class klass) {

                log("reloading: " + klass.getName());
                try {
                    org.apache.isis.core.runtime.system.context.IsisContext.getSpecificationLoader().invalidateCacheForJust(klass.getName());
                } catch (Exception e) {
                    e.printStackTrace(System.err);
                }
            }

            public int priority() {
                return 0;
            }
        };
    }

    private ClassEventListener newClassLoadListener() {
        return new ClassEventListener() {

            public void onClassEvent(int eventType, Class klass) {
                if (PersistenceCapable.class.isAssignableFrom(klass)) {

                    log("loading: " + klass.getName());
                }
            }

            public int priority() {
                return 0;
            }
        };
    }

    public boolean checkDependencies(ClassLoader classLoader, ClassResourceSource classResourceSource) {
        boolean b = classResourceSource.getClassResource("org.apache.isis.core.runtime.system.context.IsisContext") != null;
        log("Isis-JRebel-Plugin: loading? " + b);
        return true;
    }

    public String getId() {
        return "isis-jrebel-plugin";
    }

    public String getName() {
        return "Isis JRebel Plugin";
    }

    public String getDescription() {
        return "Reload Isis and JDO metadata, and enhance JDO bytes";
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
