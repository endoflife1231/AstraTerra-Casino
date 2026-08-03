package dev.astraterra.casino;

final class CurrencyBridge {
    private CurrencyBridge(){}
    static long balance(Object player) throws ReflectiveOperationException { Object c=component(player); return ((Number)Reflect.invoke(c,"getValue")).longValue(); }
    static boolean take(Object player,long value) throws ReflectiveOperationException { Object c=component(player); long b=((Number)Reflect.invoke(c,"getValue")).longValue(); if(value<0||b<value)return false; Reflect.invoke(c,"silentModify",-value);return true; }
    static void give(Object player,long value) throws ReflectiveOperationException { if(value<=0)return;Reflect.invoke(component(player),"silentModify",value); }
    private static Object component(Object player)throws ReflectiveOperationException{Object k=Reflect.staticField("com.glisco.numismaticoverhaul.ModComponents","CURRENCY");return Reflect.invoke(k,"get",player);}
}
