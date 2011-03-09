HTTP/1.1 200 OK
Server: Apache-Coyote/1.1
Set-Cookie: JSESSIONID=E5AAE8F26B7A943816CCF9370D27BE9D; Path=/
Content-Type: text/html;charset=UTF-8
Content-Language: de-DE
Transfer-Encoding: chunked
Date: Mon, 27 Jul 2009 10:27:55 GMT

d6
<html>
    <head>
        <title>Membrane Service Registry</title>
        <link rel="stylesheet" href="/css/main.css" />
        <link rel="shortcut icon" href="/images/favicon.ico" type="image/x-icon" />
        
b8a

    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    
    <meta name="layout" content="main" />
    

    <link rel="stylesheet" type="text/css" href="http://yui.yahooapis.com/2.7.0/build/fonts/fonts-min.css" />
<link rel="stylesheet" type="text/css" href="http://yui.yahooapis.com/2.7.0/build/menu/assets/skins/sam/menu.css" />
<script type="text/javascript" src="http://yui.yahooapis.com/2.7.0/build/yahoo-dom-event/yahoo-dom-event.js"></script>

<script type="text/javascript" src="http://yui.yahooapis.com/2.7.0/build/container/container_core-min.js"></script>
<script type="text/javascript" src="http://yui.yahooapis.com/2.7.0/build/menu/menu-min.js"></script>

<!--begin custom header content for this example-->

<style type="text/css">

            div.yui-b p {
            
                margin: 0 0 .5em 0;
                color: #999;
            
            }
            
            div.yui-b p strong {
            
                font-weight: bold;
                color: #000;
            
            }

            div.yui-b h2 {
            
                font-weight: bold;
                color: #000;
            
            }
            
            div.yui-b p em {

                color: #000;
            
            }            
            

            #basicmenu {
            
                margin: 0 0 10px 0;
            
            }

        </style>

<!--end custom header content for this example-->


<script type="text/javascript">

  /*
       Initialize and render the Menu when its elements are ready 
       to be scripted.
  */

  YAHOO.util.Event.onContentReady("basicmenu", function () {
  
      /*
           Instantiate a Menu:  The first argument passed to the 
           constructor is the id of the element in the page 
           representing the Menu; the second is an object literal 
           of configuration properties.
      */

      var oMenu = new YAHOO.widget.MenuBar("basicmenu", { autosubmenudisplay: true,
                                                            hidedelay: 750, 
                                                            lazyload: true });


      /*
           Call the "render" method with no arguments since the 
           markup for this Menu instance is already exists in the page.
      */

      oMenu.render();
//      oMenu.show();

      //YAHOO.util.Event.addListener("menutoggle", "click", oMenu.show, null, oMenu);
  
  });
  
</script>

    <link rel="stylesheet" type="text/css" href="http://yui.yahooapis.com/2.7.0/build/reset-fonts-grids/reset-fonts-grids.css">

  
        <script type="text/javascript" src="/js/application.js"></script>
				
    </head>
    <body>
        <div id="spinner" class="spinner" style="display:none;">
            <img src="/images/spinner.gif" alt="Spinner" />
        </div>	
        
2000

    <div class="body">
      <div class="yui-skin-sam">
  <div id="basicmenu" class="yuimenubar yuimenubarnav">
      <div class="bd">
          <ul class="first-of-type">
              <li class="yuimenubaritem first-of-type">
                  <a class="yuimenubaritemlabel" href="/">Home</a>
              </li>
              <li class="yuimenubaritem">
                  <a class="yuimenubaritemlabel" href="#services">Services</a>
                  <div id="services" class="yuimenu">
                    <div class="bd">
                      <ul>
                        <li class="yuimenuitem"><a class="yuimenuitemlabel" href="/service/register">Register Service</a></li>
                        <li class="yuimenuitem"><a class="yuimenuitemlabel" href="/consumer/list">Consumers</a></li>
                        <li class="yuimenuitem"><a class="yuimenuitemlabel" href="/endpoint/hosts">Hosts</a></li>
                      </ul>                    
                    </div>
                  </div>
              </li>
              <li class="yuimenubaritem">
                  <a class="yuimenubaritemlabel" href="#tools">Tools</a>
                  <div id="tools" class="yuimenu">
                    <div class="bd">
                      <ul>
                        <li class="yuimenuitem"><a class="yuimenuitemlabel" href="/client/start">SAOP Client</a></li>
                        <li class="yuimenuitem"><a class="yuimenuitemlabel" href="/comparator/compareWSDL">WSDL Comparator</a></li>
                        <li class="yuimenuitem"><a class="yuimenuitemlabel" href="/comparator/compareSchema">Schema Comparator</a></li>
                      </ul>                    
                    </div>
                  </div>
              </li>
              <li class="yuimenubaritem">
                  <a class="yuimenubaritemlabel" href="/schema/namespaces">Schemas</a>
              </li>
              <li class="yuimenubaritem">
                  <a class="yuimenubaritemlabel" href="#help">Help</a>
                  <div id="help" class="yuimenu">
                    <div class="bd">
                      <ul>
                        <li class="yuimenuitem"><a class="yuimenuitemlabel" href="/service/about">About</a></li>
                        <li class="yuimenuitem"><a class="yuimenuitemlabel" href="/static/repository-faq.gsp">FAQ</a></li>
                      </ul>                    
                    </div>
                  </div>
              </li>
              
                <li class="yuimenubaritem">
                    <a class="yuimenubaritemlabel" href="/account/login">Login</a>
                </li>              
              
              

          </ul>            
      </div>
  </div>
</div>

      

      <div class="yui-t4" id="doc3">
      <div id="hd">
      </div>
      <div id="bd">
      <div id="yui-main">
      <div class="yui-b">         
        <h1>Membrane Service Registry</h1>
<br />
<p>service-repository.com is a non-UDDI repository for public Web Services. You can browse Web Services and you can call their operations. The availability of the services is checked by continous service monitoring. Feel free to <a href="/service/register">register</a> yourself a service.</p>

        <br/>
        <div class="list">
          <table>
            <thead>
              <tr>
                <th></th>
                <th class="sortable" ><a href="/?sort=name&amp;order=asc">Name</a></th>
                <th>Description</th>
                <th class="sortable sorted desc" ><a href="/?sort=rating&amp;order=asc">Rating</a></th>
                <th>Availability</th>
                <th>Action<th/>
                </tr>
              </thead>
              <tbody>
                
                  <tr class="odd">
                    <td width="30">
                      <img src="/icons/tick.png" />
                      
                    </td>
                    <td>
                      <a href="/service/wsdl?id=65642">FootballPoolWebService</a>
                    </td>
                    <td width="50%">
                      
                    </td>
                    <td width="120">
                      <span>
  <img src='/images/star.png' width='16px' heigth='16px' />
  <img src='/images/star.png' width='16px' heigth='16px' />
  <img src='/images/star.png' width='16px' heigth='16px' />
  <img src='/images/star.png' width='16px' heigth='16px' />
  <img src='/images/star.png' width='16px' heigth='16px' />
</span>
                    </td>
                    <td width="80">
                      <div style="width: 60; background-color : rgb(255,0,0) ; height:15">
                          <div style="width: 60 ; background-color : rgb(0,255,0) ; height:15">
                            100%
                            <br />
                          </div>
                      </div>
                    </td>
                    <td>
                      <a href="/client/operations?wsdlURL=http%3A%2F%2Feuro2008.dataaccess.eu%2Ffootballpoolwebservice.wso%3FWSDL">Call it!</a>
                    </td>
                  </tr>
                
                  <tr class="even">
                    <td width="30">
                      <img src="/icons/lightning.png" />
                      
                    </td>
                    <td>
                      <a href="/service/wsdl?id=65670">USHolidayDates</a>
                    </td>
                    <td width="50%">
                      
                    </td>
                    <td width="120">
                      <span>
  <img src='/images/star.png' width='16px' heigth='16px' />
  <img src='/images/star.png' width='16px' heigth='16px' />
  <img src='/images/star.png' width='16px' heigth='16px' />
  <img src='/images/star.png' width='16px' heigth='16px' />
  <img src='/images/halfstar.png' width='16px' heigth='16px' />
</span>
                    </td>
                    <td width="80">
                      <div style="width: 60; background-color : rgb(255,0,0) ; height:15">
                          <div style="width: 0 ; background-color : rgb(0,255,0) ; height:15">
                            28,57%
                            <br />
                          </div>
                      </div>
                    </td>
                    <td>
                      <a href="/client/operations?wsdlURL=http%3A%2F%2Fwww.27seconds.com%2FHolidays%2FUS%2FDates%2FUSHolidayDates.asmx%3FWSDL">Call it!</a>
                    </td>
                  </tr>
                
                  <tr class="odd">
                    <td width="30">
                      <img src="/icons/tick.png" />
                      
                    </td>
                    <td>
                      <a href="/service/wsdl?id=65657">BLZService</a>
                    </td>
                    <td width="50%">
                      
                          BLZService
                      
                    </td>
                    <td width="120">
                      <span>
  <img src='/images/star.png' width='16px' heigth='16px' />
  <img src='/images/star.png' width='16px' heigth='16px' />
  <img src='/images/star.png' width='16px' heigth='16px' />
  <img src='/images/halfstar.png' width='16px' heigth='16px' />
</span>
                    </td>
                    <td width="80">
                      <div style="width: 60; background-color : rgb(255,0,0) ; height:15">
                          <div style="width: 0 ; background-color : rgb(0,255,0) ; height:15">
                            6,59%
                            <br />
                          </div>
                      </div>
                    </td>
                    <td>
                      <a href="/client/operations?wsdlURL=http%3A%2F%2Fwww.thomas-bayer.com%2Faxis2%2Fservices%2FBLZService%3Fwsdl">Call it!</a>
                    </td>
                  </tr>
                
                  <tr class="even">
                    <td width="30">
                      <img src="/icons/lightning.png" 
2000
/>
                      
                    </td>
                    <td>
                      <a href="/service/wsdl?id=65672">NamesServiceService</a>
                    </td>
                    <td width="50%">
                      
                    </td>
                    <td width="120">
                      <span>
  <img src='/images/star.png' width='16px' heigth='16px' />
  <img src='/images/star.png' width='16px' heigth='16px' />
  <img src='/images/star.png' width='16px' heigth='16px' />
</span>
                    </td>
                    <td width="80">
                      <div style="width: 60; background-color : rgb(255,0,0) ; height:15">
                          <div style="width: 0 ; background-color : rgb(0,255,0) ; height:15">
                            63,74%
                            <br />
                          </div>
                      </div>
                    </td>
                    <td>
                      <a href="/client/operations?wsdlURL=http%3A%2F%2Fwww.thomas-bayer.com%2Fnames-service%2Fsoap%3Fwsdl">Call it!</a>
                    </td>
                  </tr>
                
                  <tr class="odd">
                    <td width="30">
                      <img src="/icons/lightning.png" />
                      
                    </td>
                    <td>
                      <a href="/service/wsdl?id=65676">COPYMEService</a>
                    </td>
                    <td width="50%">
                      
                    </td>
                    <td width="120">
                      <span />
                    </td>
                    <td width="80">
                      <div style="width: 60; background-color : rgb(255,0,0) ; height:15">
                          <div style="width: 0 ; background-color : rgb(0,255,0) ; height:15">
                            0%
                            <br />
                          </div>
                      </div>
                    </td>
                    <td>
                      <a href="/client/operations?wsdlURL=http%3A%2F%2Fwww.unitesoftware.co.uk%2Fcopyme.wsdl">Call it!</a>
                    </td>
                  </tr>
                
                  <tr class="even">
                    <td width="30">
                      <img src="/icons/lightning.png" />
                      
                    </td>
                    <td>
                      <a href="/service/wsdl?id=65675">MareonService</a>
                    </td>
                    <td width="50%">
                      
                          
    	Dies ist die Web Service Beschreibung der Mareon Handwerkersoftware Schnittstelle Version 2.1.0...
                      
                    </td>
                    <td width="120">
                      <span />
                    </td>
                    <td width="80">
                      <div style="width: 60; background-color : rgb(255,0,0) ; height:15">
                          <div style="width: 0 ; background-color : rgb(0,255,0) ; height:15">
                            0%
                            <br />
                          </div>
                      </div>
                    </td>
                    <td>
                      <a href="/client/operations?wsdlURL=http%3A%2F%2Fsoap.mareon.com%2Fhwa%2Fwsdl%2Fmareonhwa.wsdl">Call it!</a>
                    </td>
                  </tr>
                
                  <tr class="odd">
                    <td width="30">
                      <img src="/icons/lightning.png" />
                      
                    </td>
                    <td>
                      <a href="/service/wsdl?id=65674">ChannelConnectService</a>
                    </td>
                    <td width="50%">
                      
                    </td>
                    <td width="120">
                      <span />
                    </td>
                    <td width="80">
                      <div style="width: 60; background-color : rgb(255,0,0) ; height:15">
                          <div style="width: 0 ; background-color : rgb(0,255,0) ; height:15">
                            0%
                            <br />
                          </div>
                      </div>
                    </td>
                    <td>
                      <a href="/client/operations?wsdlURL=http%3A%2F%2Fcert.synxis.com%2Finterface%2FChannelConnect.asmx%3FWSDL">Call it!</a>
                    </td>
                  </tr>
                
                  <tr class="even">
                    <td width="30">
                      <img src="/icons/lightning.png" />
                      
                    </td>
                    <td>
                      <a href="/service/wsdl?id=65673">Samples</a>
                    </td>
                    <td width="50%">
                      
                    </td>
                    <td width="120">
                      <span />
                    </td>
                    <td width="80">
                      <div style="width: 60; background-color : rgb(255,0,0) ; height:15">
                          <div style="width: 0 ; background-color : rgb(0,255,0) ; height:15">
                            0%
                            <br />
                          </div>
                      </div>
                    </td>
                    <td>
                      <a href="/client/operations?wsdlURL=http%3A%2F%2Fwww.digitforge.com%2Fsamples.asmx%3Fwsdl">Call it!</a>
                    </td>
                  </tr>
                
                  <tr class="odd">
                    <td width="30">
                      <img src="/icons/lightning.png" />
                      
                    </td>
                    <td>
                      <a href="/service/wsdl?id=65671">ZipCodes</a>
                    </td>
                    <td width="50%">
                      
                    </td>
                    <td width="120">
                      <span />
                    </td>
                    <td width="80">
                      <div style="width: 60; background-color : rgb(255,0,0) ; height:15">
                          <div style="width: 0 ; background-color : rgb(0,255,0) ; height:15">
                            48,35%
                            <br />
                          </div>
                      </div>
                    </td>
                    <td>
                      <a href="/client/operations?wsdlURL=http%3A%2F%2Fwww.jasongaylord.com%2Fwebservices%2Fzipcodes.asmx%3FWSDL">Call it!</a>
                    </td>
                  </tr>
                
                  <tr class="even">
                    <td width="30">
                      <img src="/icons/lightning.png" />
                      
                    </td>
                    <td>
                      <a href="/service/wsdl?id=65669">IP2Geo</a>
                    </td>
                    <td width="50%">
                      
                    </td>
                    <td width="120">
                      <span />
                    </td>
                    <td width="80">
                      <div style="width: 60; background-color : rgb(255,0,0) ; height:15">
                          <div style="width: 0 ; background-color : rgb(0,255,0) ; height:15">
                            27,47%
                            <br />
                          </div>
                      </div>
                    </td>
                    <td>
                      <a href="/client/operations?wsdlURL=http%3A%2F%2Fws.cdyne.com%2Fip2geo%2Fip2geo.asmx%3Fwsdl">Call it!</a>
                    </td>
                  </tr>
                
              </tbody>
          </table>
        </div>
        <div class="paginateButtons">
            <span class="currentStep">1</span><a href="/?offset=10&amp;max=10&amp;sort=rating&amp;order=desc" class="step">2</a><a href="/?offset=20&amp;max=10&amp;sort=rating&amp;order=desc" class="step">3</a><a href="/?offset=30&amp;max=10&amp;sort=rating&amp;order=desc" class="step">4</a><a href
2000
="/?offset=10&amp;max=10&amp;sort=rating&amp;order=desc" class="nextLink">Next</a>
        </div>
        <br />
        <h2>Featured Services</h2>
        <p>
          These services implement some use cases showing some advanced featured like dependency and consumer management.
        </p>
        <div class="list">
          <table>
            <thead>
              <tr>
                <th></th>
                <th class="sortable" ><a href="/?sort=name&amp;order=asc">Name</a></th>
                <th>Description</th>
                <th class="sortable sorted desc" ><a href="/?sort=rating&amp;order=asc">Rating</a></th>
                <th>Availability</th>
                <th>Action<th/>
                </tr>
              </thead>
              <tbody>
                
                  <tr class="odd">
                    <td width="30">
                      <img src="/icons/lightning.png" />
                      
                    </td>
                    <td>
                      <a href="/service/wsdl?id=65677">ArticleService</a>
                    </td>
                    <td width="50%">
                      
                                  The ArticleService manages all articles available in the shop.
    
                      
                    </td>
                    <td width="120">
                      <span />
                    </td>
                    <td width="80">
                      <div style="width: 60; background-color : rgb(255,0,0) ; height:15">
                          <div style="width: 0 ; background-color : rgb(0,255,0) ; height:15">
                            2,2%
                            <br />
                          </div>
                      </div>
                    </td>
                    <td>
                      <a href="/client/operations?wsdlURL=http%3A%2F%2Fwww.predic8.com%3A8080%2Fmaterial%2FArticleService%3Fwsdl">Call it!</a>
                    </td>
                  </tr>
                
                  <tr class="even">
                    <td width="30">
                      <img src="/icons/lightning.png" />
                      
                    </td>
                    <td>
                      <a href="/service/wsdl?id=65678">IDService</a>
                    </td>
                    <td width="50%">
                      
                          The IDService is an id generator and manager for other applications.
                      
                    </td>
                    <td width="120">
                      <span />
                    </td>
                    <td width="80">
                      <div style="width: 60; background-color : rgb(255,0,0) ; height:15">
                          <div style="width: 0 ; background-color : rgb(0,255,0) ; height:15">
                            2,2%
                            <br />
                          </div>
                      </div>
                    </td>
                    <td>
                      <a href="/client/operations?wsdlURL=http%3A%2F%2Fwww.predic8.com%3A8080%2Fbase%2FIDService%3Fwsdl">Call it!</a>
                    </td>
                  </tr>
                
                  <tr class="odd">
                    <td width="30">
                      <img src="/icons/lightning.png" />
                      
                    </td>
                    <td>
                      <a href="/service/wsdl?id=65679">ShopService</a>
                    </td>
                    <td width="50%">
                      
                                  The ShopService is one of a few sample services for the Membrane SOA tools. Feel free to use ...
                      
                    </td>
                    <td width="120">
                      <span />
                    </td>
                    <td width="80">
                      <div style="width: 60; background-color : rgb(255,0,0) ; height:15">
                          <div style="width: 0 ; background-color : rgb(0,255,0) ; height:15">
                            2,2%
                            <br />
                          </div>
                      </div>
                    </td>
                    <td>
                      <a href="/client/operations?wsdlURL=http%3A%2F%2Fwww.predic8.com%3A8080%2Fshop%2FShopService%3Fwsdl">Call it!</a>
                    </td>
                  </tr>
                
                  <tr class="even">
                    <td width="30">
                      <img src="/icons/lightning.png" />
                      
                    </td>
                    <td>
                      <a href="/service/wsdl?id=65680">CRMService</a>
                    </td>
                    <td width="50%">
                      
                                  The CustomerService manages the customer relationships.
    
                      
                    </td>
                    <td width="120">
                      <span />
                    </td>
                    <td width="80">
                      <div style="width: 60; background-color : rgb(255,0,0) ; height:15">
                          <div style="width: 0 ; background-color : rgb(0,255,0) ; height:15">
                            2,2%
                            <br />
                          </div>
                      </div>
                    </td>
                    <td>
                      <a href="/client/operations?wsdlURL=http%3A%2F%2Fwww.predic8.com%3A8080%2Fcrm%2FCustomerService%3Fwsdl">Call it!</a>
                    </td>
                  </tr>
                
              </tbody>
            </table>
          </div>
          <br />
          <br />
          <br />
          <div>
            
              <h2>Events</h2>
              <table>
                <thead>
                  <th/>
                  <th>Date</th>
                  <th>Description</th>
                  <th>Action</th>
                </thead>
                <tbody>
                  
                    <tr>
                      <td width="30">
                        <img src="/icons/accept.png" />
                      </td>
                      <td width="200">
                        2009-07-27 08:49:58 GMT
                      </td>
                      <td>
                        Service: <a href='/service/wsdl?id=65658'>TempConvert</a> available again
                      </td>
                      <td width="200">
                        
                      </td>
                    </tr>
                  
                    <tr>
                      <td width="30">
                        <img src="/icons/accept.png" />
                      </td>
                      <td width="200">
                        2009-07-27 08:49:58 GMT
                      </td>
                      <td>
                        Service: <a href='/service/wsdl?id=65657'>BLZService</a> available again
                      </td>
                      <td width="200">
                        
                      </td>
                    </tr>
                  
                    <tr>
                      <td width="30">
                        <img src="/icons/accept.png" />
                      </td>
                      <td width="200">
                        2009-07-27 07:49:57 GMT
                      </td>
                      <td>
                        Service: <a href='/service/wsdl?id=65659'>DOTSPhoneAppend</a> available again
                      </td>
                      <td width="200">
                        
                      </td>
                    </tr>
                  
                    <tr>
                      <td width="30">
                        <img src="/icons/delete.png" />
                      </td>
                      <td width="200">
                        2009-07-27 04:49:37 GMT
                      </td>
                      <td>
                        Service: <a href='/service/wsdl?id=65659'>DOTSPhoneAppend</a> went down
                      </td>
                      <td width="200">
            
1574
            
                      </td>
                    </tr>
                  
                    <tr>
                      <td width="30">
                        <img src="/icons/delete.png" />
                      </td>
                      <td width="200">
                        2009-07-27 04:49:37 GMT
                      </td>
                      <td>
                        Service: <a href='/service/wsdl?id=65658'>TempConvert</a> went down
                      </td>
                      <td width="200">
                        
                      </td>
                    </tr>
                  
                    <tr>
                      <td width="30">
                        <img src="/icons/delete.png" />
                      </td>
                      <td width="200">
                        2009-07-27 04:49:37 GMT
                      </td>
                      <td>
                        Service: <a href='/service/wsdl?id=65657'>BLZService</a> went down
                      </td>
                      <td width="200">
                        
                      </td>
                    </tr>
                  
                    <tr>
                      <td width="30">
                        <img src="/icons/accept.png" />
                      </td>
                      <td width="200">
                        2009-07-27 03:49:39 GMT
                      </td>
                      <td>
                        Service: <a href='/service/wsdl?id=65659'>DOTSPhoneAppend</a> available again
                      </td>
                      <td width="200">
                        
                      </td>
                    </tr>
                  
                    <tr>
                      <td width="30">
                        <img src="/icons/accept.png" />
                      </td>
                      <td width="200">
                        2009-07-27 03:49:39 GMT
                      </td>
                      <td>
                        Service: <a href='/service/wsdl?id=65658'>TempConvert</a> available again
                      </td>
                      <td width="200">
                        
                      </td>
                    </tr>
                  
                    <tr>
                      <td width="30">
                        <img src="/icons/accept.png" />
                      </td>
                      <td width="200">
                        2009-07-27 03:49:39 GMT
                      </td>
                      <td>
                        Service: <a href='/service/wsdl?id=65657'>BLZService</a> available again
                      </td>
                      <td width="200">
                        
                      </td>
                    </tr>
                  
                    <tr>
                      <td width="30">
                        <img src="/icons/delete.png" />
                      </td>
                      <td width="200">
                        2009-07-26 22:49:59 GMT
                      </td>
                      <td>
                        Service: <a href='/service/wsdl?id=65672'>NamesServiceService</a> went down
                      </td>
                      <td width="200">
                        
                      </td>
                    </tr>
                  
                </tbody>
              </table>
              <a href="/evententry/list">more</a>
            
          </div>
          <br />
          <br />
          <br />
          <div>
  <h2>News</h2>
   <br />
   <h3>2008/05/27 - Sample News</h3>
   <p>
     <br />
     If you can read this your current tenant is set to sample. To change the current tenant visit the <a href="/admin/index">admin page</a>. 
   
 </div>
          <br />
          <br />
          <br />
        </div>
        </div>
        <div class="yui-b">
          <p>
            <h1 class="line">Registry Software</h2>
            You can install Membrane Registry at your Company to manage your services. <a href="http://www.predic8.com/registry-tour/service-dependencies.htm">Take a tour</a> and see the advanced features like WSDL version monitoring or dependency Management. <a href="http://www.predic8.com/open-source/soa-registry-repository/">Download the registry</a> and start monitoring in minutes.
          </p>
          <p>
            <h1 class="line">Web Services Tools</h2>
            <ul>
            <li><a href="/client/start">Dynamic SOAP Client</a></li>
            <li><a href="/comparator/compareWSDL">WSDL Version Diff</a></li>
            <li><a href="/comparator/compareSchema">Schema Version Diff</a></li>            
            </ul>
          </p>
          <h1 class="line">Tags</h1>
          <div class="cloud">
            
          </div>         
        </div>
        </div>
        <div id="ft">
            In case of questions or problems please contact <a href="mailto:info@predic8.de">info@predic8.de</a> <!--See what service repository can do for <a href="/for-consumers.htm">Web Services Consumer</a> and <a href="/for-providers.htm">Web Services Provider</a>. -->
            <br />
          <br />
          <div class="foot">
  <div class="logo"><img src="/images/p8-110.png" alt="predic8" /></div>	
</div>
          <a href="http://www.predic8.de">SOA & Web Services Training</a>
        </div>
        </div>
      </div>
    		
    </body>	
</html>
0

