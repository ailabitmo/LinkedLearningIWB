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

        String clientCode = "startIt('" + contID + "', '/aov/aov/pageviewer/', '" + spaqlUrl + "', '" + path + "')";
        addClientUpdate(new FClientUpdate(FClientUpdate.Prio.VERYEND, clientCode));
        StringBuilder html = new StringBuilder();
        html.append(
                "<div id='middle'>\n" +
                "    <div id='container'>\n" +
                "        <div id='" + contID + "'></div>\n" +
                "    </div>\n" +
                "</div>");
        return html.toString();
    }

}
