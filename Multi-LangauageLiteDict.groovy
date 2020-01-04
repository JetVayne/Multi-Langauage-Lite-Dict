import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.net.URLEncoder;
import java.net.URLDecoder;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.io.*;
import java.util.ArrayList;
import org.jsoup.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Node;
import org.jsoup.Connection;
import org.jsoup.Connection.Response;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Element;
import org.jsoup.helper.HttpConnection;
import org.json.*;
import java.sql.Timestamp;
import java.net.URL;
import org.apache.log4j.*;
import org.apache.log4j.xml.DOMConfigurator;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.sql.*;
import java.util.Properties;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import groovy.transform.Field;

@Field Logger logger = Logger.getLogger("wiki");
@Field String runUrl;
@Field String runStep;
@Field String srcUrl = "https://zh.wikipedia.org/zh-tw/Category:%E7%B6%93%E6%BF%9F"; // wiki-category-ul
@Field String mainCate = "經濟"; // wiki-category
@Field String lang = "zh-tw";	// the language you want, ex: ar, ko, ja, fr, th...
@Field int stopLevel = 5;	// the level of this category you want to get

// load log4j
DOMConfigurator.configure("C:/log4j.xml");
try {
	
	start();
	
} catch (Exception e) {
	logger.error(getStackTrace(e));
}

def void start(){

    if(lang == "zh-cn"){
        srcUrl = srcUrl.replace("https://zh.wikipedia.org/wiki/","https://zh.wikipedia.org/zh-cn/")
    }else if(lang != "zh-tw"){
        srcUrl = getLangUrl(srcUrl);
        if(srcUrl == null){
            logger.info("The category-["+mainCate+"] may not have language-["+lang+"]. Please check the zh_tw page.");
            return;
        }
    }

    int level = 1;
    getData(level);

}

def String getLangUrl(String input){
    String result;
    try{
        Document doc = getDoc(input);
        String langUrl =  doc.select("div#p-lang > div.body > ul > li > a[lang="+lang+"]").attr("href");
        if(langUrl.length()!=0){
            result = langUrl;
        }

    }catch(Exception e){
        logger.error("Failed to get ["+lang+"]-Wiki Url of ["+mainCate+"].");
        logger.error(getStackTrace(e));
    }

    return result
}

def void getData(Integer level){
    runStep = "[getData]";
    Document doc = getDoc(srcUrl);
    String pageType = getPageType(doc);

    if(pageType.equals("cate")){
        String id = new URL(srcUrl).getFile();
        String title = doc.select("h1#firstHeading").text();
        String pid = new URL(srcUrl).getFile();
        HashMap subCate = getSubCate(doc,srcUrl);
        HashMap wikiUrlList = getWikiUrl(doc,srcUrl);
        HashMap output = cateSave(id,pid,level,mainCate,title,srcUrl,subCate,wikiUrlList,lang);
		//outputList.add(output);

        List<HashMap> diveList = new ArrayList<String>();

        if(subCate.values().size()!=0){
            HashMap temp = new HashMap();
            temp.put(id,new ArrayList<String>(subCate.values()));
            diveList.add(temp);
        }

        if(diveList.size()!=0){
            recursive(level, diveList);
        }

    }else if(pageType.equals("wiki")){
        println "This page is a wiki page.";
    }
}

def void recursive(Integer level, List cateUrlList){
    runStep = "[recursive]";
    level++;

    if(level>stopLevel){
        return;
    }

    List<HashMap> diveList = new ArrayList<String>();

    for(HashMap data: cateUrlList){
        try{
            String pid = data.keySet().toArray()[0];
            for(String cateUrl: data.get(pid)){
                Document doc = getDoc(cateUrl);
                String id = new URL(cateUrl).getFile();
                String title = doc.select("h1#firstHeading").text();
                HashMap subCate = getSubCate(doc,cateUrl);
                HashMap wikiUrlList = getWikiUrl(doc,cateUrl);
                HashMap output = cateSave(id,pid,level,mainCate, title,cateUrl,subCate,wikiUrlList,lang);
				//outputList.add(output);

                if(subCate.values().size()!=0){
                    HashMap temp = new HashMap();
                    temp.put(id,new ArrayList<String>(subCate.values()));
                    diveList.add(temp);
                }
            }
        }catch(Exception e){
            logger.info("failed to parse a subCate in diveList.")
        }
    }

    if(diveList.size()!=0){
        recursive(level, diveList);
    }
}

def HashMap getSubCate(Document input,String url){
    runStep = "[getSubCate]";
    HashMap result = new HashMap();
    Document src = input.clone();
    src.select("div#Categoryarticlecount-box").remove();
    Elements subCateList = src.select("div#mw-subcategories").select("ul > li").select("a");
    for(Element subCate: subCateList){
        String subCateName = subCate.text();

        String link = subCate.attr("href");
        String subCateUrl = new URL(new URL(url), link);
        if(lang=="zh-tw"){
            subCateUrl = subCateUrl.replace("https://zh.wikipedia.org/wiki/","https://zh.wikipedia.org/zh-tw/")
        }else if(lang=="zh-cn"){
            subCateUrl = subCateUrl.replace("https://zh.wikipedia.org/wiki/","https://zh.wikipedia.org/zh-cn/")
        }
        result.put(subCateName,subCateUrl);

    }

    return result
}




def HashMap getWikiUrl(Document input,String url){
    runStep = "[getWikiUrl]";
    HashMap result = new HashMap();
    Document src = input.clone();
    src.select("div#Categoryarticlecount-box").remove();
    Elements wikiList = src.select("div#mw-pages").select("ul > li").select("a");
    for(Element wiki: wikiList){
        String link = wiki.attr("href");
        String wikiUrl = new URL(new URL(url), link);
        String wikiName = wiki.text();

        if(lang=="zh-tw"){
            wikiUrl = wikiUrl.replace("https://zh.wikipedia.org/wiki/","https://zh.wikipedia.org/zh-tw/")
        }else if(lang=="zh-cn"){
            wikiUrl = wikiUrl.replace("https://zh.wikipedia.org/wiki/","https://zh.wikipedia.org/zh-cn/")
        }
        result.put(wikiName,wikiUrl);

    }

    return result;
}

def String getPageType(Document input){
    String result = "D";
    if(input.select("div#mw-pages").size() !=0){
        return "cate"
    }else{
        if(input.select("div#mw-subcategories").size()==0){
            return "wiki"
        }else{
            return "cate"
        }
    }

    return result
}


def HashMap cateSave(String id, String pid, Integer level, String category,String title, String url, HashMap subcategory, HashMap page, String language){
    HashMap result = new HashMap();
    result.put("id",MD5(id));
    result.put("pid",MD5(pid));
    result.put("level",level);
    result.put("category",category);
    result.put("title",splitMessage(title));
    result.put("url",url);
    result.put("subcategory",splitMessage(toJSON(subcategory)));
    result.put("page",splitMessage(toJSON(page)));
    result.put("language",language);

    return result;
}

def String toJSON(HashMap input){
    String result;
    if(input.size()==0){
        return result;
    }
    try{
        JSONObject temp = new JSONObject(input);
        result = temp.toString();
    }catch(Exception e){
        logger.info("failed to convert subCate HashMap to JSON.")
        logger.error(getStackTrace(e));
    }
    return result;
}

def String splitMessage(String input) {
    String result="";
    if(input==null){
        return input;
    }
    for (String token: input.split("\u0000")) {
        result = result + token;
    }
    return result;
}

def String MD5(String input) {
    if (input == null){
        return null;
	}
    try {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(input.getBytes());
        String md5 = new BigInteger(1, md.digest()).toString(16);
        return fillMD5(md5);
    } catch (Exception e) {
        throw new RuntimeException("MD5 failed: " + e.getMessage(), e);
    }
}

def String fillMD5(String md5) {
    return md5.length() == 32 ? md5 : fillMD5("0" + md5);
}

def Document getDoc(String url){
	runUrl = url;
	Document result;
	for(int i = 0;i<3;i++){
		try{
			result = Jsoup.connect(url).get();
			if(result!=null){

				return result;
			}else{
				Thread.sleep(2000);
				continue;
			}
		}catch(Exception e){
			logger.error("Failed to get Document. url: "+url);
		}
	}

	return result;
}

def getStackTrace(e) {
    StringWriter sw = new StringWriter();
    e.printStackTrace(new PrintWriter(sw));
    return sw.toString();
}