package dev.astraterra.casino;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Predicate;

final class RewardCommand {
    private RewardCommand(){}
    static void register(){
        try{
            Object event=Reflect.staticField("net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback","EVENT");
            Class<?> cb=Reflect.cls("net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback");
            Object proxy=Proxy.newProxyInstance(cb.getClassLoader(),new Class<?>[]{cb},(p,m,a)->{
                if(m.getDeclaringClass()==Object.class)return objectMethod(p,m,a,"RewardCommandCallback");
                if(a!=null&&a.length>0)build(a[0]);return null;
            });
            Reflect.invoke(event,"register",proxy);
        }catch(Throwable e){throw new IllegalStateException("Could not register astraterra_reward command",e);}
    }
    private static void build(Object dispatcher)throws Exception{
        Class<?> lab=Reflect.cls("com.mojang.brigadier.builder.LiteralArgumentBuilder");
        Object root=Reflect.invokeStatic(lab.getName(),"literal","astraterra_reward");
        Predicate<Object> permission=source->{try{return ((Number)Reflect.field(source,"field_9815")).intValue()>=2;}catch(Throwable e){return false;}};
        Reflect.invoke(root,"requires",permission);
        Class<?> commandType=Reflect.cls("com.mojang.brigadier.Command");
        for(int tier=1;tier<=5;tier++){
            final int t=tier;
            Object sub=Reflect.invokeStatic(lab.getName(),"literal",Integer.toString(tier));
            Object cmd=Proxy.newProxyInstance(commandType.getClassLoader(),new Class<?>[]{commandType},(p,m,a)->{
                if(m.getDeclaringClass()==Object.class)return objectMethod(p,m,a,"RewardTier"+t);
                Object ctx=a[0];Object source=Reflect.invoke(ctx,"getSource");Object player=Reflect.invoke(source,"method_44023");return giveRandom(player,t);
            });
            Reflect.invoke(sub,"executes",cmd);Reflect.invoke(root,"then",sub);
        }
        Reflect.invoke(dispatcher,"register",root);
        System.out.println("[AstraTerra Casino] Registered secure tier reward command");
    }
    private static int giveRandom(Object player,int maxTier){
        try{
            Object registry=Reflect.staticField("net.minecraft.class_7923","field_41178");
            List<Object> candidates=new ArrayList<>();List<String> ids=new ArrayList<>();
            for(Object item:(Iterable<?>)registry){
                Object idObj=Reflect.invoke(registry,"method_10221",item);if(idObj==null)continue;String id=idObj.toString();
                if(technical(id))continue;int tier=tier(id);if(tier<=maxTier){int weight=tier==maxTier?5:Math.max(1,4-(maxTier-tier));for(int i=0;i<weight;i++){candidates.add(item);ids.add(id);}}}
            if(candidates.isEmpty()){CasinoEngine.msg(player,"§cПул случайной награды пуст.");return 0;}
            int index=new Random().nextInt(candidates.size());Object item=candidates.get(index);String id=ids.get(index);Object stack=Reflect.construct("net.minecraft.class_1799",item,1);Boolean accepted=(Boolean)Reflect.invoke(player,"method_7270",stack);if(Boolean.FALSE.equals(accepted))Reflect.invoke(player,"method_7328",stack,true);
            CasinoEngine.msg(player,"§6Награда экспедиции T"+maxTier+": §f"+id+(Boolean.FALSE.equals(accepted)?" §7(инвентарь заполнен)":""));return 1;
        }catch(Throwable e){System.err.println("[AstraTerra Casino] Random reward failed");e.printStackTrace();return 0;}
    }
    private static boolean technical(String id){String p=id.substring(id.indexOf(':')+1);return p.equals("air")||p.contains("command_block")||p.contains("structure_void")||p.contains("structure_block")||p.contains("jigsaw")||p.contains("debug_stick")||p.contains("knowledge_book")||p.contains("barrier")||p.contains("light_")||p.equals("light");}
    private static int tier(String id){String s=id.toLowerCase(Locale.ROOT);if(has(s,"netherite","elytra","dragon_egg","nether_star","legendary","mythic","artifact","relic","ancient","boss","soul_","warden","end_crystal"))return 5;if(has(s,"diamond","epic","unique","hammer","greatsword","scythe","staff","spell","aircraft","backpack","totem","enchanted"))return 4;if(has(s,"iron","gold","rare","sword","bow","crossbow","armor","helmet","chestplate","leggings","boots","trident","potion","gem","crystal"))return 3;if(has(s,"copper","ingot","food","meal","decor","brick","plank","log","stone","glass","boat","saddle","tool"))return 2;return 1;}
    private static boolean has(String s,String... needles){for(String n:needles)if(s.contains(n))return true;return false;}
    private static Object objectMethod(Object p,Method m,Object[] a,String name){return switch(m.getName()){case"toString"->name;case"hashCode"->System.identityHashCode(p);case"equals"->p==(a==null?null:a[0]);default->null;};}
}
