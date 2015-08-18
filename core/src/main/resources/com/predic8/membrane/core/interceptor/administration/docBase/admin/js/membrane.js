var membrane = function() {
			
	function createLink(href, content, params) {
		var i,
		    url = encodeURI(href);
		
		if (params) {
			for (i = 0; i < params.length; i++) {
				if (i == 0) {
					url += "?";
				} else {
					url += "&";
				}
				url += params[i][0]+"="+encodeURIComponent(params[i][1]);
			}			
		}
		
		return '<a href="'+url+'">'+content+'</a>';
	}
	
	function loadExchange(exchangeId) {	
		var exchangeUrl = relativeRootPath + '/admin/rest/exchanges/'+exchangeId,

			responseBodyUrl = exchangeUrl+'/response/body',
			responseRawUrl = exchangeUrl+'/response/raw',
			responseBodyBeautifiedUrl = relativeRootPath + '/admin/web/exchanges/'+exchangeId+'/response/body',
			responseHeaderUrl = exchangeUrl+'/response/header',
	
			requestBodyUrl = exchangeUrl+'/request/body',
			requestRawUrl = exchangeUrl+'/request/raw',
			requestBodyBeautifiedUrl = relativeRootPath + '/admin/web/exchanges/'+exchangeId+'/request/body',
			requestHeaderUrl = exchangeUrl+'/request/header';

		function getHeaderTableConfiguration(url) {		
		  return {
				  'bJQueryUI': true,
				  "bFilter": false,
				  "bInfo": false,
				  "bPaginate": false,
				  "bServerSide": true,
				  "sAjaxSource": url,
				  "sAjaxDataProp": "headers",
				  "aoColumns": [
				                { "mDataProp": "name" },
				                { "mDataProp": "value" }
				              ],
		    "fnServerData": function ( sSource, aoData, fnCallback ) {
		        $.ajax( {            	  
		          "dataType": 'json', 
		          "type": "GET", 
		          "url": sSource, 
		          "data": [], 
		          "success": function(data) {   
		          	data.sEcho = aoData.sEcho;
		          	data.iTotalRecords = data.headers.length;
		          	data.iTotalDisplayRecords = data.headers.length;
		          	fnCallback(data);
		          }
		        } );
		      }		              
		  	};
		}
		
		function getMetaTableConfiguration(propsFunc) {
			return {
				  'bJQueryUI': true,
				  "bFilter": false,
				  "bInfo": false,
				  "bPaginate": false,
				  "bServerSide": true,
				  "sAjaxSource": exchangeUrl,
	  		      "fnServerData": function ( sSource, aoData, fnCallback ) {
				        $.ajax( {            	  
				          "dataType": 'json', 
				          "type": "GET", 
				          "url": sSource, 
				          "data": [], 
				          "success": function(data) {
				        	fnCallback({
				        		sEcho:aoData.sEcho,
				        		aaData: propsFunc(data),
				        		iTotalRecords:propsFunc(data).length, 
				        		iTotalDisplayRecords:propsFunc(data).length
				        	});
				          }
				        } );
				    }		              
		  	}
		}

		function loadText(selector, url) {			
			$.get(url, function(resp) {
				if ($.browser.msie  && parseInt($.browser.version, 10) <= 8) {
					document.getElementById(selector.substring(1)).innerText = resp;
				} else {
					$(selector).text(resp);
				}
			}, 'text');				
		}
		
		function setHTML(selector, html) {
				if ($.browser.msie  && parseInt($.browser.version, 10) <= 8) {
					document.getElementById(selector.substring(1)).innerHTML = html.replace(/> /g, ">&nbsp;");
				} else {
			$(selector).html(html);
			}
		}
		
		$('#request-download-button').button({icons: {primary:'ui-icon-circle-arrow-s'}}).attr('href', requestBodyUrl);
		$('#response-download-button').button({icons: {primary:'ui-icon-circle-arrow-s'}}).attr('href', responseBodyUrl);

		$.get(exchangeUrl, function(exc) {
			function isXMLContentType(s) {
				return s == 'text/xml' || (s.indexOf('application/') == 0 && s.indexOf('xml', s.length - 3) != -1);
			}
			
			if (isXMLContentType(exc.respContentType)) {
				$.get(responseBodyBeautifiedUrl, function(resp) {
					setHTML('#response-body', resp);
				});
			} else {
				loadText('#response-body', responseBodyUrl);
			}	
			
			if (isXMLContentType(exc.reqContentType)) {
				$.get(requestBodyBeautifiedUrl, function(resp) {
					setHTML('#request-body', resp);
				});
			} else {
				loadText('#request-body', requestBodyUrl);
			}			
		}, 'json');

		$('#request-meta').dataTable(getMetaTableConfiguration(function(data) {
			var fullPath = "http://"+data.server+":"+data.serverPort+data.path;
		return [
		   ["Time", data.time],
		   ["Method", data.method],
   		   ["Path", data.method=="GET"?createLink(fullPath, data.path):data.path],
		   ["Proxy", createLink(relativeRootPath + '/admin/service-proxy/show', data.proxy, [['name',data.proxy+':'+data.listenPort]])],
		   ["Client", createLink(relativeRootPath + '/admin/calls',  data.client, [['client', data.client]])],
		   ["Content Type", data.reqContentType],
		   ["Length", data.reqContentLength]
		];}));
		$('#request-headers').dataTable(getHeaderTableConfiguration(requestHeaderUrl));
		loadText('#request-raw', requestRawUrl);
		
		$('#response-meta').dataTable(getMetaTableConfiguration(function(data) { return [
		   ["Status Code", data.statusCode],
		   ["Server", data.server],
		   ["Content Type", data.respContentType],
		   ["Length", data.respContentLength],
		   ["Duration", data.duration]
		];}));
		$('#response-headers').dataTable(getHeaderTableConfiguration(responseHeaderUrl));	
		loadText('#response-raw', responseRawUrl);
	}
	
	return {
		createLink:createLink,
		loadExchange:loadExchange
	}
}();

$(function() {
	
	$('#proxy-rules-table, #interceptor-table, #statistics-table, #stream-pumps-table, #statuscode-table' ).dataTable({
	  'bJQueryUI': true,
	  'sPaginationType': 'full_numbers'
	});
	
	$('.balancersTable, .clustersTable, .sessionsTable').dataTable({'bJQueryUI': true, 'bPaginate': false});
	
	$('#clients-table').dataTable({		
		  'bJQueryUI': true,
		  "sPaginationType": 'full_numbers',
		  "bFilter": false,
		  "bInfo": true,
		  "bServerSide": true,
		  "sAjaxSource": relativeRootPath + '/admin/rest/clients',
		  "sAjaxDataProp": "clients",
		  "aoColumns": [
		                { "mDataProp": "name" },
		                { "mDataProp": "count" },
		                { "mDataProp": "min" },
		                { "mDataProp": "max" },
		                { "mDataProp": "avg" }
		              ],
		  "aoColumnDefs": [ 
		           {
		               "fnRender": function ( o, v ) {
		                   return membrane.createLink(relativeRootPath + '/admin/calls', v, [['client', v]]);
		               },
		               "aTargets": [ 0 ]
		           }],
			"fnServerData": function ( sSource, aoData, fnCallback ) {
		      	  function getParam(name) {
		      		  function byName(it) {
		      			  return it.name == name;
		      		  }
		      		  return $.grep(aoData, byName)[0].value;
		      	  }
				
			    $.ajax( {            	  
			      "dataType": 'json', 
			      "type": "GET", 
			      "url": sSource, 
	              "data": [{name:'offset', value:getParam('iDisplayStart')},
	                       {name:'max', value:getParam('iDisplayLength')},
	                       {name:'sort', value:getParam('mDataProp_'+getParam('iSortCol_0'))},
	                       {name:'order', value:getParam('sSortDir_0')}], 
			      "success": function(data) {   
			      	data.sEcho = aoData.sEcho;
			      	data.iTotalRecords = data.total;
			      	data.iTotalDisplayRecords = data.total;
			      	fnCallback(data);
			      }
			    } );
			  }		              
  	});
	
	$('#fwdrules-table').dataTable({
		  'bJQueryUI': true,
		  'sPaginationType': 'full_numbers',
		  'iDisplayLength' : 25,
		  "bProcessing": true,
		  "bServerSide": true,
		  "bDestroy":true,
		  "sAjaxSource": relativeRootPath + '/admin/rest/proxies',
		  "sAjaxDataProp": "proxies",
		  "aoColumns": [
		                { "mDataProp": "order" },
		                { "mDataProp": "name" },
		                { "mDataProp": "listenPort" },
		                { "mDataProp": "virtualHost" },
		                { "mDataProp": "method" },
		                { "mDataProp": "path" },
		                { "mDataProp": "targetHost" },
		                { "mDataProp": "targetPort" },
		                { "mDataProp": "count" },
		                { "mDataProp": "actions" }
		              ],
		  "aoColumnDefs": [ 
	           {
	               "fnRender": function ( o, v ) {
	            	   r = membrane.createLink(relativeRootPath + '/admin/service-proxy/show', v, [['name',v+':'+o.aData.listenPort]]);
	            	   if (!o.aData['active']) {
	            	     error = o.aData['error'];
	            	     if (!error)
	            	     	error = "";
	            	     r = r + " " + '<span class="proxy-state ui-icon ui-icon-alert" title="This service is not active: ' + error.replace(/"/g, "&quot;") + '. Click &quot;play&quot; to retry."></span>';
	            	   }
	            	   return r;
	               },
	               "aTargets": [ 1 ]
	           },
	           {
	               "fnRender": function ( o, v ) {
	               		r = "";
	            		if (v['delete']) {
	            			r += '<a class="action" href="'+v['delete']+'" title="Delete"><span class="ui-icon ui-icon-trash"></span></a>';
	           			}
	           			if (v['start']) {
	            			r += '<a class="action" href="'+v['start']+'" title="Start"><span class="ui-icon ui-icon-play"></span></a>';
	         			}
						return r;
	               },
	               "aTargets": [ 9 ]
	           }
		  ],
         "fnServerData": function ( sSource, aoData, fnCallback ) {
      	  function getParam(name) {
      		  function byName(it) {
      			  return it.name == name;
      		  }
      		  return $.grep(aoData, byName)[0].value;
      	  }
      	  
            $.ajax( {            	  
              "dataType": 'json', 
              "type": "GET", 
              "url": sSource, 
              "data": [{name:'offset', value:getParam('iDisplayStart')},
                       {name:'max', value:getParam('iDisplayLength')},
                       {name:'sort', value:getParam('mDataProp_'+getParam('iSortCol_0'))},
                       {name:'order', value:getParam('sSortDir_0')}], 
              "success": function(data) {   
              	data.sEcho = aoData.sEcho;
              	data.iTotalRecords = data.total;
              	data.iTotalDisplayRecords = data.total;
              	fnCallback(data);
              }
            } );
          }		              
	});

	$('.hovericon').hover(
		function(){ $(this).addClass('ui-state-hover'); }, 
		function(){ $(this).removeClass('ui-state-hover'); }
	);
	$('.hovericon').click(function(){ $(this).toggleClass('ui-state-active'); });
	$('.icons').append(' <a href="#">Toggle text</a>').find('a').click(function(){ $('.icon-collection li span.text').toggle(); return false; }).trigger('click');

	membrane.messageTable = $('#message-stat-table').dataTable({
		  "aaSorting": [[0,'desc']],
		  "bFilter": false,		  
		  'bJQueryUI': true,
		  'sPaginationType': 'full_numbers',
		  'iDisplayLength' : 25,
		  "bProcessing": true,
		  "bServerSide": true,
		  "bDestroy":true,
		  "sAjaxSource": relativeRootPath + '/admin/rest/exchanges',
		  "sAjaxDataProp": "exchanges",
		  "aoColumnDefs": [ 
	           {
	               "fnRender": function ( o, v ) {
	            	   return membrane.createLink(relativeRootPath + '/admin/call', v, [['id',o.aData.id]]);
	               },
	               "aTargets": [ 0 ]
	           },
	           {
	               "fnRender": function ( o, v ) {
	            	   return membrane.createLink(relativeRootPath + '/admin/service-proxy/show', v, [['name',v+':'+o.aData.listenPort]]);
	               },
	               "aTargets": [ 2 ]
	           }
		  ],
		  "aoColumns": [
		                { "mDataProp": "time" },
		                { "mDataProp": "statusCode" },
		                { "mDataProp": "proxy" },
		                { "mDataProp": "method" },
		                { "mDataProp": "path" },
		                { "mDataProp": "client" },
		                { "mDataProp": "server" },
		                { "mDataProp": "reqContentType" },
		                { "mDataProp": "reqContentLength" },
		                { "mDataProp": "respContentType" },
		                { "mDataProp": "respContentLength" },
		                { "mDataProp": "duration" }
		              ],
          "fnServerData": function ( sSource, aoData, fnCallback ) {
        	  var queryData = [{name:'offset', value:getParam('iDisplayStart')}, 
                          {name:'max', value:getParam('iDisplayLength')},
                          {name:'sort', value:getParam('mDataProp_'+getParam('iSortCol_0'))},
                          {name:'order', value:getParam('sSortDir_0')}];
        	  
        	  function addFilterProps(name) {
        		  if ($("#message-filter-"+name).val()!='*') {
        			  queryData.push({name:name, value:$("#message-filter-"+name).val()});
        		  }
        	  }
        	  
        	  function getParam(name) {
        		  function byName(it) {
        			  return it.name == name;
        		  }
        		  return $.grep(aoData, byName)[0].value;
        	  }
        	  
        	  addFilterProps('statuscode');
        	  addFilterProps('method');
        	  addFilterProps('proxy');
        	  addFilterProps('client');
        	  addFilterProps('server');
        	  addFilterProps('reqcontenttype');
        	  addFilterProps('respcontenttype');
        	  
              $.ajax( {            	  
                "dataType": 'json', 
                "type": "GET", 
                "url": sSource, 
                "data": queryData, 
                "success": function(data) {   
                	data.sEcho = aoData.sEcho;
                	data.iTotalRecords = data.total;
                	data.iTotalDisplayRecords = data.total;
                	fnCallback(data);
                }
              } );
            }		              
	});
			
	$('#tabs li').hover(
    	    function () {
    	    	    $(this).addClass("ui-state-hover");
    	    },
    	    function () {
    	    	    $(this).removeClass("ui-state-hover");
    	    }
    );
    
    $('.mb-button').button();

	$('#reloadData').click(function() {
		$('#message-stat-table').dataTable()._fnAjaxUpdate();
	});

    $('form').validationEngine('attach', {promptPosition : 'bottomRight', scroll: false});
    $('form').submit(function() {
		return this.validationEngine('validate');
    });			
});
