import com.ollamapocket.app.OllamaLibrarySearch;
import java.util.List;

public final class OllamaLibrarySearchTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        String page="<a href=\"/library/qwen3\">Qwen3</a>" +
            "<a class='result' href='/library/qwen3.5?sort=downloads'>Qwen 3.5</a>" +
            "<a href=\"/library/gemma3\">Gemma 3</a>" +
            "<a href=\"/library/qwen3\">duplicate</a>" +
            "<a href=\"/john/community\">community</a>" +
            "<a href=\"javascript:alert(1)\">bad</a>" +
            "<a href=\"/library/../a\">traversal</a>" +
            "<a href=\"https://ollama.com/library/deepseek-r1\">remote official</a>" +
            "<a href=\"/library/qwen3:0.6b\">individual tags are not family results</a>";
        List<String> list=OllamaLibrarySearch.parseOfficialModels(page);
        check(list.size()==4,"official model links only");
        check(list.get(0).equals("qwen3"),"preserve relevant ordering");
        check(list.contains("qwen3.5"),"allow versioned names");
        check(list.contains("gemma3"),"Gemma");
        check(list.contains("deepseek-r1"),"https official");
        check(!list.contains("john"),"no fabricated community model");
        check(!list.contains("a"),"no traversal");
        check(OllamaLibrarySearch.parseOfficialModels("").isEmpty(),"empty page");
        check(OllamaLibrarySearch.parseOfficialModels(null).isEmpty(),"null page");
        System.out.println("PASS: OllamaLibrarySearch (9 assertions)");
    }
}
