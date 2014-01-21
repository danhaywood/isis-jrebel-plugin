package com.danhaywood.isis.tool.jrebelplugin;


public class CustomClassLoader extends ClassLoader {
    public CustomClassLoader(ClassLoader parent) {
        super(parent);
    }

    public synchronized void defineClass(String fullClassName, byte[] bytes) {
        try {
            defineClass(fullClassName, bytes, 0, bytes.length);
        } finally {
        }
    }
}
