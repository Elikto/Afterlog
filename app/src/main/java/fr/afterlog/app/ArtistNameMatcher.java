package fr.afterlog.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Safe artist-name variations: try exact first, then remove decorative
 * emoji or a leading DJ title. Never derive a profile URL from a name. */
final class ArtistNameMatcher {
    static String normalized(String raw){
        if(raw==null)return "";
        return Normalizer.normalize(raw.toLowerCase(Locale.ROOT).replace("ø","o"),
            Normalizer.Form.NFD).replaceAll("\\p{M}","")
            .replaceAll("[^a-z0-9]+"," ").trim().replaceAll("\\s+"," ");
    }
    static List<String> variants(String name){
        LinkedHashSet<String> names=new LinkedHashSet<>();
        if(name==null)return new ArrayList<>();
        String raw=name.trim().replaceAll("\\s+"," ");
        if(!raw.isEmpty())names.add(raw);
        // Strip emoji, symbols and decorative brackets around the displayed name.
        String plain=raw.replaceAll("[\\p{So}\\p{Sk}\\p{Cs}\\p{Cf}\\uFE0E\\uFE0F]"," ")
            .replaceAll("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$","")
            .replaceAll("\\s+"," ").trim();
        if(!plain.isEmpty())names.add(plain);
        String unprefixed=plain.replaceFirst("(?i)^(?:dj)[\\s._:-]+","").trim();
        if(unprefixed.length()>=3)names.add(unprefixed);
        return new ArrayList<>(names);
    }
    static boolean compatible(String source,String expected){
        String actual=normalized(source);
        if(actual.isEmpty())return false;
        for(String variant:variants(expected))if(actual.equals(normalized(variant)))
            return true;
        return false;
    }
}
