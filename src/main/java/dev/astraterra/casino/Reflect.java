package dev.astraterra.casino;

import java.lang.reflect.*;
import java.util.*;

final class Reflect {
    private Reflect() {}
    static Class<?> cls(String name) throws ClassNotFoundException { return Class.forName(name); }
    static Object staticField(String c, String f) throws ReflectiveOperationException { Field x=findField(cls(c),f); x.setAccessible(true); return x.get(null); }
    static Object field(Object o,String f)throws ReflectiveOperationException{Field x=findField(o.getClass(),f);x.setAccessible(true);return x.get(o);}
    private static Field findField(Class<?> c,String n)throws NoSuchFieldException{for(Class<?> t=c;t!=null;t=t.getSuperclass()){try{return t.getDeclaredField(n);}catch(NoSuchFieldException ignored){}}throw new NoSuchFieldException(c.getName()+"."+n);}
    static Object invoke(Object target,String name,Object... args) throws ReflectiveOperationException { Method m=findMethod(target.getClass(),name,false,args); m.setAccessible(true); return m.invoke(target,args); }
    static Object invokeStatic(String c,String name,Object... args) throws ReflectiveOperationException { Method m=findMethod(cls(c),name,true,args); m.setAccessible(true); return m.invoke(null,args); }
    static Object construct(String c,Object... args) throws ReflectiveOperationException {
        Class<?> type=cls(c);
        for(Constructor<?> k:type.getConstructors()){ if(matches(k.getParameterTypes(),args)){k.setAccessible(true);return k.newInstance(args);} }
        for(Constructor<?> k:type.getDeclaredConstructors()){ if(matches(k.getParameterTypes(),args)){k.setAccessible(true);return k.newInstance(args);} }
        throw new NoSuchMethodException("constructor "+c+Arrays.toString(args));
    }
    static Method findMethod(Class<?> type,String name,boolean stat,Object... args)throws NoSuchMethodException{
        for(Class<?> t=type;t!=null;t=t.getSuperclass()){
            for(Method m:t.getDeclaredMethods()) if(m.getName().equals(name)&&Modifier.isStatic(m.getModifiers())==stat&&matches(m.getParameterTypes(),args)) return m;
        }
        for(Method m:type.getMethods()) if(m.getName().equals(name)&&Modifier.isStatic(m.getModifiers())==stat&&matches(m.getParameterTypes(),args)) return m;
        throw new NoSuchMethodException(type.getName()+"."+name+Arrays.toString(args));
    }
    private static boolean matches(Class<?>[] p,Object[] a){ if(p.length!=a.length)return false; for(int i=0;i<p.length;i++){ if(a[i]==null){if(p[i].isPrimitive())return false;} else {Class<?> q=p[i].isPrimitive()?wrap(p[i]):p[i]; if(!q.isAssignableFrom(a[i].getClass()))return false;}} return true; }
    private static Class<?> wrap(Class<?> p){ if(p==int.class)return Integer.class;if(p==long.class)return Long.class;if(p==boolean.class)return Boolean.class;if(p==byte.class)return Byte.class;if(p==short.class)return Short.class;if(p==float.class)return Float.class;if(p==double.class)return Double.class;if(p==char.class)return Character.class;return p; }
}
