package ru.ifmo.ailab;

import com.fluidops.ajax.FClientUpdate;
import com.fluidops.ajax.components.FComponent;
import com.fluidops.iwb.api.EndpointImpl;
import com.fluidops.util.Rand;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Created with IntelliJ IDEA.
 * User: yuemelyanov
 * Date: 13.11.13
 * Time: 11:01
 */
public class OntoViewer extends FComponent {

    String path;

    public OntoViewer(String id, String startPath) {
        super(id);
        this.path = startPath;
    }

    @Override
    public String render() {
        String contID = "ontograph"+Rand.getIncrementalFluidUUID();
        URI url = null;
        try {
            url = new URI(getParent().getHttpRequestInit().getRequestURL().toString());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        String spaqlUrl = "http://" + url.getHost() + ":" + url.getPort() + "/sparql";
        addClientUpdate(new FClientUpdate(FClientUpdate.Prio.VERYEND, "startIt('" + contID + "', '/oed/oed/rootviewer/', '" + spaqlUrl + "', '" + path + "')"));
        StringBuilder html = new StringBuilder();
        html.append(
                "<input style=\"visibility: collapse;\" type=\"text\" id='prefixList' value=\"http://www.semanticweb.org/k0shk/ontologies/2013/5/learning, http://purl.org/vocab/aiiso/schema\"/>"+
                "<div id='middle'>\n" +
                "    <div id='container'>\n" +
                "        <div id='" + contID + "'></div>\n" +
                "    </div>\n" +
                "</div>");
//        for ( String head : jsURLs() )
//            html.append( "<script type='text/javascript' src='" ).append( head ).append( "'></script>\n" );
//        for ( String head : cssURLs() )
//            html.append( "<link rel='stylesheet' type='text/css' href='" ).append( head ).append( "'/>\n" );
        return html.toString();
    }

}
