/* Copyright 2021 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.templating;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.resolver.ResourceRetrievalException;
import org.json.JSONObject;
import org.junit.*;
import org.mockito.Mockito;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class TemplateInterceptorTest {

    TemplateInterceptor ti;
    Exchange exc = new Exchange(null);
    Request req;
    static Path copiedXml;
    static Path copiedJson;
    static Router router;
    static ResolverMap map;

    @BeforeClass
    public static void setupFiles() throws IOException {
        copyFiles(Paths.get("src/test/resources/xml/project_template.xml"),Paths.get(System.getProperty("user.dir") +
                FileSystems.getDefault().getSeparator() + "project_template.xml") );
        copyFiles(Paths.get("src/test/resources/json/template_test.json"), Paths.get(System.getProperty("user.dir") +
                FileSystems.getDefault().getSeparator() + "template_test.json"));
        
        copiedXml = Paths.get(System.getProperty("user.dir") +
                FileSystems.getDefault().getSeparator() + "project_template.xml");
        copiedJson = Paths.get(System.getProperty("user.dir") +
                FileSystems.getDefault().getSeparator() + "template_test.json");
        router = Mockito.mock(Router.class);
        map = new ResolverMap();
        Mockito.when(router.getResolverMap()).thenReturn(map);
    }

    @Before
    public void setUp(){
        ti = new TemplateInterceptor();
        exc = new Exchange(null);
        req = new Request.Builder().build();
        exc.setRequest(req);

        exc.setProperty("title", "minister");
        List<String> lst = new ArrayList<String>();
        lst.add("food1");
        lst.add("food2");
        exc.setProperty("items", lst);
        exc.setProperty("title", "minister");

    }

    @Test
    public void xmlFromFileTest() throws Exception {
        setAndHandleRequest("./project_template.xml");

        XPathExpression xpath = XPathFactory.newInstance().newXPath().compile("/project/part[2]/title");
        String filled = ((NodeList) xpath.evaluate(DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(exc.getRequest().getBodyAsStream()), XPathConstants.NODESET)).item(0).getFirstChild().getNodeValue();

        Assert.assertEquals("minister", filled.trim());
    }


    @Test
    public void nonXmlTemplateListTest() throws Exception {
        setAndHandleRequest("./template_test.json");

        Assert.assertEquals("food1",
                new JSONObject(exc.getRequest().getBodyAsStringDecoded()).getJSONArray("orders")
                        .getJSONObject(0).getJSONArray("items").getString(0));

        Assert.assertEquals("minister",
                new JSONObject(exc.getRequest().getBodyAsStringDecoded()).getJSONObject("meta").getString("title"));

    }

    @Test(expected = IllegalStateException.class)
    public void initTest() throws Exception {
        ti.setLocation("./template_test.json");
        ti.setTextTemplate("${minister}");
        ti.init(router);
    }

    @Test(expected = ResourceRetrievalException.class)
    public void notFoundTemplateException() throws Exception {
        ti.setLocation("./not_existent_file");
        ti.init(router);
    }

    @Test
    public void innerTagTest() throws Exception {
        ti.setTextTemplate("${title}");
        ti.init(router);
        ti.handleRequest(exc);

        Assert.assertEquals("minister", exc.getRequest().getBodyAsStringDecoded());
    }

    @Test
    public void contentTypeTestXml() throws Exception {
        setAndHandleRequest("./project_template.xml");
        Assert.assertTrue(exc.getRequest().isXML());
    }

    @Test
    public void contentTypeTestOther() throws Exception {
        ti.setContentType("application/json");
        setAndHandleRequest("./template_test.json");
        Assert.assertTrue(exc.getRequest().isJSON());
    }

    @Test
    public void contentTypeTestNoXml() throws Exception {
        setAndHandleRequest("./template_test.json");
        Assert.assertNull(exc.getRequest().getHeader().getContentType());
    }

//    @Test
//    public void extractorAndTemplateTest() throws Exception{
//        XmlPathExtractorInterceptor xpe = new XmlPathExtractorInterceptor();
//        xpe.getMappings().add(new XmlPathExtractorInterceptor.Property("/project/part[2]/item", "items"));
//        xpe.handleRequest(exc);
//
//        assertEquals("25", ((List)exc.getProperty("items")).get(1));
//
//    }

    private void setAndHandleRequest(String location) throws Exception {
        ti.setLocation(location);
        ti.init(router);
        ti.handleRequest(exc);
    }

    @AfterClass
    public static  void deleteFile() throws IOException {
        Files.delete(copiedXml);
        Files.delete(copiedJson);
    }

    public static void copyFiles(Path orig, Path copy) throws IOException {
        Files.copy(orig, copy, StandardCopyOption.REPLACE_EXISTING);
    }
}