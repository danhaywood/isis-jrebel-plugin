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
import java.net.MalformedURLException;
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

import org.datanucleus.api.jdo.JDOPersistenceManagerFactory;
import org.datanucleus.api.jdo.metadata.JDOMetaDataManager;
import org.datanucleus.enhancer.DataNucleusEnhancer;
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

public class ClassloadingExperiments {

    public static void main(String[] args) throws ClassNotFoundException, MalformedURLException {
        
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

        ClassLoader parentClassLoader = ClassLoader.getSystemClassLoader();
        List<URL> urls = new ArrayList<URL>(Arrays.asList(((URLClassLoader) contextClassLoader).getURLs()));
        urls.add(new URL("file:///C:/APACHE/isis-git-rw/example/application/simple_wicket_restful_jdo/dom/target-ide/classes/"));
        URL[] array = urls.toArray(new URL[]{});
        for (URL url : array) {
            System.err.println("url: " + url.toString());
        }
        
        URLClassLoader classLoader = new URLClassLoader(array, parentClassLoader);
        System.err.println("our classLoader is: " + classLoader);

        String className = "dom.simple.SimpleObjects";
        
        Class<?> cls = classLoader.loadClass(className );
        System.err.println("    loaded: " + cls.getName());
        System.err.println("    - classloader: " + cls.getClassLoader().toString());
        System.err.println("    - methods:");
        Method[] methods = cls.getMethods();
        for (Method method : methods) {
            System.err.println("      - method: " + method.toString());
        }

//            URLClassLoader classLoader = new URLClassLoader(
//                    new URL[]{
//                            new URL("file:///C:/APACHE/isis-git-rw/example/application/simple_wicket_restful_jdo/dom/target-ide/classes/")
//                           ,new URL("file:///C:/Users/Dan/.m2/repository/javax/jdo/jdo-api/3.1-rc1/jdo-api-3.1-rc1.jar")
//                           }, parentClassLoader);

    }
}
