package fr.afterlog.app;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import java.net.URI;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Reads the public "À propos" section of an EVENT, not the SEO/og summary,
 * ticket sales text, venue biography or a site's About footer. */
final class ShotgunAboutExtractor {
    static boolean validEventUrl(String raw){
        try{
            URI u=new URI(raw);
            String host=u.getHost(),p=u.getPath();
            return "https".equalsIgnoreCase(u.getScheme()) && host!=null &&
                (host.equalsIgnoreCase("shotgun.live")||
                 host.toLowerCase(Locale.ROOT).endsWith(".shotgun.live")) &&
                p!=null && p.matches("^/(?:[a-z]{2}/)?(?:events|festivals)/[^/]+/?$");
        }catch(Exception ignored){return false;}
    }
    static String readPublic(String url) throws Exception {
        if(!validEventUrl(url))return "";
        Document doc=Jsoup.connect(url).timeout(15000).maxBodySize(3_000_000)
            .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            .followRedirects(true).get();
        if(!validEventUrl(doc.location()))return "";
        return extract(doc);
    }
    private static String key(String text){
        String name=Normalizer.normalize(text==null?"":text,Normalizer.Form.NFD)
            .replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).trim()
            .replaceAll("\\s+"," ").replaceAll("[:：]$","");
        return name.trim();
    }
    private static boolean about(String title){
        String k=key(title);
        return k.equals("a propos")||k.equals("about")||k.equals("description");
    }
    private static boolean boundary(String title){
        return key(title).matches("line.?up|programmation|artistes|artists|vibe|localisation|location|organise par|billets|tickets|infos pratiques");
    }
    private static String content(Element candidate){
        if(candidate==null||candidate.tagName().matches("nav|footer|header"))return "";
        Element copy=candidate.clone();
        copy.select("button,script,style,noscript,[aria-hidden=true],[role=button]").remove();
        for(Element br:copy.select("br"))br.replaceWith(new TextNode("\n"));
        for(Element block:copy.select("p,li,blockquote,div,section,h4,h5"))
            block.appendText("\n");
        String raw=copy.wholeText().replace('\u00a0',' ')
            .replaceAll("[ \\t]+\\n","\n")
            .replaceAll("\\n[ \\t]+","\n")
            .replaceAll("\\n{3,}","\n\n").trim();
        if(raw.isEmpty()||raw.length()>16000||about(raw)||boundary(raw))return "";
        if(raw.matches("(?s).*(?m:^(?:Line.?up|Localisation|Organis[eé] par)\\s*$).*"))
            return "";
        return raw;
    }
    static String extract(Document doc){
        Element main=doc.selectFirst("main");
        if(main==null)main=doc.selectFirst("article");
        if(main==null)return "";
        for(Element label:main.select("h2,h3,h4,h5,[role=heading],[role=tab],button,strong,span,p,div")){
            if(!about(label.text())||label.childrenSize()>2||
               label.closest("footer,header,nav,a[href],[aria-hidden=true]")!=null)continue;
            List<Element> containers=new ArrayList<>();
            String controlled=label.attr("aria-controls");
            if(!controlled.isEmpty())containers.add(doc.getElementById(controlled));
            containers.add(label.nextElementSibling());
            Element parent=label.parent();
            if(parent!=null){
                containers.add(parent.nextElementSibling());
                if(parent.childrenSize()<=5)for(Element child:parent.children())
                    if(child!=label&&!child.getAllElements().contains(label))
                        containers.add(child);
                Element section=label.closest("section,[data-testid*=about],[class*=about],[class*=About]");
                if(section!=null&&section!=main){
                    Element cloned=section.clone();
                    Element heading=cloned.selectFirst("h2,h3,h4,[role=heading]");
                    if(heading!=null&&about(heading.text()))heading.remove();
                    containers.add(cloned);
                }
            }
            for(Element candidate:containers){
                if(candidate==null||candidate==main||
                    candidate.getAllElements().contains(label))continue;
                boolean unrelated=false;
                for(Element heading:candidate.select("h2,h3,[role=heading]"))
                    if(boundary(heading.text())){unrelated=true;break;}
                if(unrelated)continue;
                String text=content(candidate);
                if(!text.isEmpty())return text;
            }
        }
        return "";
    }
}
