package com.sinx.platform.auth;
import java.util.Map;
final class ProviderJson {
 private ProviderJson() {}
 static String text(Map<?,?> data,String key) {
  if(data==null || !(data.get(key) instanceof String s) || s.isBlank()) throw new LoginFailure("identity_invalid");return s;
 }
 static Map<?,?> object(Map<?,?> data,String key) {
  if(data==null || !(data.get(key) instanceof Map<?,?> nested)) throw new LoginFailure("identity_invalid");return nested;
 }
 static int number(Map<?,?> data,String key,int fallback) {return data!=null && data.get(key) instanceof Number n ? n.intValue() : fallback;}
 static void success(Map<?,?> data,String key) {if(number(data,key,-1)!=0) throw new LoginFailure("provider_error");}
}
