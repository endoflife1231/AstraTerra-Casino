package dev.astraterra.casino;

import java.io.*;
import java.nio.file.*;
import java.util.*;

final class CasinoData {
    private static final Properties P = new Properties();
    private static Path file;
    private static boolean loaded;
    private CasinoData() {}

    private static synchronized void ensure() {
        if (loaded) return;
        loaded=true;
        try {
            Object loader=Reflect.invokeStatic("net.fabricmc.loader.api.FabricLoader","getInstance");
            Path config=(Path)Reflect.invoke(loader,"getConfigDir");
            Files.createDirectories(config);
            file=config.resolve("astraterra-casino-data.properties");
            if(Files.exists(file)) try(InputStream in=Files.newInputStream(file)){P.load(in);}
        } catch(Throwable e){
            file=null;
            System.err.println("[AstraTerra Casino] Could not load persistent data");
            e.printStackTrace();
        }
    }

    static String id(Object player){
        try {
            Object profile=Reflect.invoke(player,"method_7334");
            Object uuid=Reflect.invoke(profile,"getId");
            if(uuid!=null){
                String value=String.valueOf(uuid).trim();
                if(!value.isEmpty()&&!value.equalsIgnoreCase("null"))return value;
            }
            Object name=Reflect.invoke(profile,"getName");
            if(name!=null){
                String value=String.valueOf(name).trim().toLowerCase(Locale.ROOT);
                if(!value.isEmpty())return "name:"+value.replaceAll("[^a-z0-9_.-]","_");
            }
        }catch(Throwable ignored){}
        return "unknown-"+System.identityHashCode(player);
    }

    static synchronized long get(Object p,String key,long def){
        ensure();
        try{return Long.parseLong(P.getProperty(fullKey(p,key),Long.toString(def)));}
        catch(Exception e){return def;}
    }

    static synchronized boolean set(Object p,String key,long value){
        ensure();
        String k=fullKey(p,key);String old=P.getProperty(k);
        P.setProperty(k,Long.toString(Math.max(0,value)));
        if(save())return true;
        restore(k,old);return false;
    }

    static synchronized boolean change(Object p,String key,long delta){
        ensure();
        String k=fullKey(p,key);String old=P.getProperty(k);
        long before=parse(old,0);long after=before+delta;
        if(after<0)return false;
        P.setProperty(k,Long.toString(after));
        if(save())return true;
        restore(k,old);return false;
    }

    static synchronized long add(Object p,String key,long delta){
        long before=get(p,key,0);long after=Math.max(0,before+delta);
        return set(p,key,after)?after:before;
    }


    static synchronized boolean beginEscrow(Object p,long value){
        ensure();
        if(value<=0)return true;
        String wallet=fullKey(p,"wallet"), escrow=fullKey(p,"escrow");
        String oldWallet=P.getProperty(wallet), oldEscrow=P.getProperty(escrow);
        long w=parse(oldWallet,0), e=parse(oldEscrow,0);
        if(w<value)return false;
        P.setProperty(wallet,Long.toString(w-value));
        P.setProperty(escrow,Long.toString(e+value));
        if(save())return true;
        restore(wallet,oldWallet);restore(escrow,oldEscrow);return false;
    }

    static synchronized boolean refundEscrow(Object p){
        ensure();
        String wallet=fullKey(p,"wallet"), escrow=fullKey(p,"escrow");
        String oldWallet=P.getProperty(wallet), oldEscrow=P.getProperty(escrow);
        long e=parse(oldEscrow,0);
        if(e<=0)return true;
        P.setProperty(wallet,Long.toString(parse(oldWallet,0)+e));
        P.setProperty(escrow,"0");
        if(save())return true;
        restore(wallet,oldWallet);restore(escrow,oldEscrow);return false;
    }

    static synchronized long escrow(Object p){return get(p,"escrow",0);}

    static synchronized boolean settleEscrows(Collection<?> players,Map<String,Long> payouts){
        ensure();
        Map<String,String> old=new HashMap<>();
        try{
            for(Object p:players){
                String wallet=fullKey(p,"wallet"), escrow=fullKey(p,"escrow");
                old.put(wallet,P.getProperty(wallet));old.put(escrow,P.getProperty(escrow));
                P.setProperty(escrow,"0");
                long payout=Math.max(0,payouts.getOrDefault(id(p),0L));
                P.setProperty(wallet,Long.toString(parse(old.get(wallet),0)+payout));
            }
            if(save())return true;
        }catch(Throwable ignored){}
        for(Map.Entry<String,String> e:old.entrySet())restore(e.getKey(),e.getValue());
        return false;
    }

    static synchronized boolean flag(Object p,String key){return get(p,"flag."+key,0)>0;}
    static synchronized void flag(Object p,String key,boolean value){set(p,"flag."+key,value?1:0);}

    private static String fullKey(Object p,String key){return id(p)+"."+key;}
    private static long parse(String value,long def){try{return value==null?def:Long.parseLong(value);}catch(Exception e){return def;}}
    private static void restore(String key,String old){if(old==null)P.remove(key);else P.setProperty(key,old);}

    private static boolean save(){
        if(file==null)return false;
        Path temp=null;
        try{
            temp=Files.createTempFile(file.getParent(),"astraterra-casino-",".tmp");
            try(OutputStream out=Files.newOutputStream(temp,StandardOpenOption.TRUNCATE_EXISTING)){P.store(out,"AstraTerra Casino player data");}
            try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
            return true;
        }catch(IOException e){
            System.err.println("[AstraTerra Casino] Could not atomically save player data");
            e.printStackTrace();
            if(temp!=null)try{Files.deleteIfExists(temp);}catch(IOException ignored){}
            return false;
        }
    }
}
