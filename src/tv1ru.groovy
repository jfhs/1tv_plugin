import org.serviio.library.online.ContentURLContainer
import org.serviio.library.online.PreferredQuality
import org.serviio.library.online.WebResourceContainer
import org.serviio.library.online.WebResourceItem
import org.serviio.library.online.WebResourceUrlExtractor
import org.w3c.dom.Document
import org.xml.sax.InputSource

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

class tv1ru extends WebResourceUrlExtractor {

    final String VALID_WEBRESOURCE_URL = '(^http://www\\.1tv\\.ru/videoarchiver/pr=[0-9]+.*$)'
    final String RSS_URL = 'http://www.1tv.ru/owa/win/ONE_ONLINE_VIDEOS.archive_single_xml?one=1&pid='
    final String PAGE_URL = "http://www.1tv.ru/owa/win/ONE_ONLINE_VIDEOS.last_video_project?cnt="
    final Map<String, Integer> months = [
            'Января': 0,
            'Февраля': 1,
            'Марта': 2,
            'Апреля': 3,
            'Мая': 4,
            'Июня': 5,
            'Июля': 6,
            'Августа': 7,
            'Сентября': 8,
            'Октября': 9,
            'Ноября': 10,
            'Декабря': 11,
    ];

    @Override
    protected WebResourceContainer extractItems(URL url, int maxItems) {

        WebResourceContainer result = new WebResourceContainer()

        String text = getUrl(url)
        int vcnt = videoCount(text)
        result.title = pageTitle(text)
        result.items.addAll(parsePage(text))
        int added = result.items.size()
        int page = 2;
        while(((maxItems == -1) || (result.items.size() < maxItems)) && (added > 0))
        {
            URL u = pageUrl(url, page, vcnt)
            text = getUrl(u)
            if (pageNumber(text) < page)
                break
            List<WebResourceItem> r = parsePage(text)
            added = r.size()
            result.items.addAll(r)
            ++page
        }

        return result
    }

    @Override
    protected ContentURLContainer extractUrl(WebResourceItem webResourceItem, PreferredQuality preferredQuality) {
        ContentURLContainer result = new ContentURLContainer();
        String text = getUrl(new URL(RSS_URL + webResourceItem.additionalInfo["vid"]))
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document d = dBuilder.parse(new InputSource(new StringReader(text)))
        result.thumbnailUrl = d.getElementsByTagName("media:thumbnail").item(0).getAttributes().getNamedItem("url").getNodeValue()
        result.contentUrl = d.getElementsByTagName("media:content").item(0).getAttributes().getNamedItem("url").getNodeValue()
        return result;
    }

    @Override
    boolean extractorMatches(URL url) {
        return url ==~ VALID_WEBRESOURCE_URL;
    }

    @Override
    String getExtractorName() {
        return getClass().getName()
    }

    private URL pageUrl(URL start, int page, int vcnt) {
        String str = PAGE_URL + vcnt + "&p_pagenum=" + page;

        def match = start.toString() =~ 'pr=([0-9]+)'
        if (match.count > 0)
            str += "&project_id=" + match[0][1];
        match = start.toString() =~ 'v=([0-9]+)'
        if (match.count > 0)
            str += "&video_p=" + match[0][1];
        match = start.toString() =~ 'id=([0-9\\-]+)';
        if (match.count > 0)
            str += "&project_id=" + match[0][1];
        match = start.toString() =~ 'f=([0-9]+)';
        if (match.count > 0)
            str += "&film_id=" + match[0][1];

        return new URL(str);
    }

    private String getUrl(URL u) {
        try {
            return u.text;
        }
        catch (Exception e)
        {
            log(e.toString());
            return null;
        }
    }

    private static String pageTitle(String text) {
        def match = text =~ '<div class="tv_head-ins">(.+)<span>'
        assert match != null
        return match[0][1]
    }

    private static int pageNumber(String text) {
        def match = text =~ '<strong>([0-9]+)</strong>'
        if (match == null)
            return 0
        return Integer.parseInt(match[0][1])
    }

    private static int videoCount(String text) {
        def match = text =~ '- \\[([0-9]+)\\]</span>'
        if (match == null) {
            return -1
        }
        return Integer.parseInt(match[0][1])
    }

    private List<WebResourceItem> parsePage(String text) {
        def matches = text =~ '(?s)<a href="(/videoarchive/[^"]+)"><img src="([^"]+)" alt="([^"]+)".+?<div class="date">([^<]+)</div>'
        List<WebResourceItem> result = []
        for(String[] i: matches)
        {
            WebResourceItem item = new WebResourceItem()
            item.title = i[3]
            def m2 = i[1] =~ '/videoarchive/([0-9]+)'
            item.additionalInfo = ['vid': m2[0][1], 'WebResourceItemThumbnailUrl': i[2]];
            item.releaseDate = parseDate(new String(i[4].getBytes("UTF-8"), "windows-1251"))
            result << item
        }
        return result
    }

    private Date parseDate(String text) {
        String[] parts = text.split(' ');
        if (!months.containsKey(parts[1]))
        {
            return null;
        }
        return new Date(Integer.parseInt(parts[2]) - 1900, months[parts[1]], Integer.parseInt(parts[0]));
    }

    static void main(args) {
        def self = new tv1ru()

        def tu = new URL("http://www.1tv.ru/videoarchiver/id=-2&f=6222&pg=1")
        WebResourceContainer res = self.extractItems(tu, -1)
        if (!self.extractorMatches(tu))
            println "ERROR!";
        println res.title + "(" + res.items.size() + ")"
        for(int i = 0; i < res.items.size(); ++i) {
            def r2 = self.extractUrl(res.items[i], PreferredQuality.HIGH);
            println res.items[i].title;
            println r2.contentUrl
            println res.items[i].releaseDate;
            println "----"
        }
    }
}
