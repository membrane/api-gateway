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
		var exchangeUrl = '/admin/rest/exchanges/'+exchangeId,

			responseBodyUrl = exchangeUrl+'/response/body',
			responseRawUrl = exchangeUrl+'/response/raw',
			responseBodyBeautifiedUrl = '/admin/web/exchanges/'+exchangeId+'/response/body',
			responseHeaderUrl = exchangeUrl+'/response/header',
	
			requestBodyUrl = exchangeUrl+'/request/body',
			requestRawUrl = exchangeUrl+'/request/raw',
			requestBodyBeautifiedUrl = '/admin/web/exchanges/'+exchangeId+'/request/body',
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
				$(selector).text(resp);
			}, 'text');				
		}
		
		$('#request-download-button').button({icons: {primary:'ui-icon-circle-arrow-s'}}).attr('href', requestBodyUrl);
		$('#response-download-button').button({icons: {primary:'ui-icon-circle-arrow-s'}}).attr('href', responseBodyUrl);

		$.get(exchangeUrl, function(exc) {
			if (exc.respContentType == 'text/xml') {
				$.get(responseBodyBeautifiedUrl, function(resp) {
					$('#response-body').html(resp);
				});
			} else {
				loadText('#response-body', responseBodyUrl);
			}	
			
			if (exc.reqContentType == 'text/xml') {
				$.get(requestBodyBeautifiedUrl, function(resp) {
					$('#request-body').html(resp);
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
		   ["Proxy", createLink('/admin/service-proxy/show', data.proxy, [['name',data.proxy+':'+data.listenPort]])],
		   ["Client", createLink('/admin/calls',  data.client, [['client', data.client]])],
		   ["Content Type", data.reqContentType],
		   ["Length", data.reqContentLength],
		];}));
		$('#request-headers').dataTable(getHeaderTableConfiguration(requestHeaderUrl));
		loadText('#request-raw', requestRawUrl);
		
		$('#response-meta').dataTable(getMetaTableConfiguration(function(data) { return [
		   ["Status Code", data.statusCode],
		   ["Server", data.server],
		   ["Content Type", data.respContentType],
		   ["Length", data.respContentLength],
		   ["Duration", data.duration],
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
	
	$('#proxy-rules-table, #interceptor-table, #statistics-table, #statuscode-table' ).dataTable({
	  'bJQueryUI': true,
	  'sPaginationType': 'full_numbers'
	});
	
	$('#clients-table').dataTable({		
		  'bJQueryUI': true,
		  "sPaginationType": 'full_numbers',
		  "bFilter": false,
		  "bInfo": true,
		  "bServerSide": true,
		  "sAjaxSource": '/admin/rest/clients',
		  "sAjaxDataProp": "clients",
		  "aoColumns": [
		                { "mDataProp": "name" },
		                { "mDataProp": "count" },
		                { "mDataProp": "min" },
		                { "mDataProp": "max" },
		                { "mDataProp": "avg" },
		              ],
		  "aoColumnDefs": [ 
		           {
		               "fnRender": function ( o, v ) {
		                   return membrane.createLink('/admin/calls', v, [['client', v]]);
		               },
		               "aTargets": [ 0 ]
		           },
		           {
		               "fnRender": function ( o, v ) {
		                   return v+" ms";
		               },
		               "aTargets": [ 2 ]
		           },
		           {
		               "fnRender": function ( o, v ) {
		                   return v+" ms";
		               },
		               "aTargets": [ 3 ]
		           },
		           {
		               "fnRender": function ( o, v ) {
		                   return v+" ms";
		               },
		               "aTargets": [ 4 ]
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
			      	data.iTotalRecords = data.clients.length;
			      	data.iTotalDisplayRecords = data.clients.length;
			      	fnCallback(data);
			      }
			    } );
			  }		              
  	});
	
	$('#fwdrules-table').dataTable({
		  'bJQueryUI': true,
		  'sPaginationType': 'full_numbers',
		  "bProcessing": true,
		  "bServerSide": true,
		  "bDestroy":true,
		  "sAjaxSource": '/admin/rest/proxies',
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
		                { "mDataProp": "actions" },
		              ],
		  "aoColumnDefs": [ 
	           {
	               "fnRender": function ( o, v ) {
	            	   return membrane.createLink('/admin/service-proxy/show', v, [['name',v+':'+o.aData.listenPort]]);
	               },
	               "aTargets": [ 1 ]
	           },
	           {
	               "fnRender": function ( o, v ) {
	            	   if (v['delete']) {
	            		   return '<a href="'+v['delete']+'"><span class="ui-icon ui-icon-trash"></span></a>';
	            	   }
	                   return '';
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

	membrane.messageTable = $('#message-stat-table').dataTable({
		  "aaSorting": [[0,'desc']],
		  "bFilter": false,		  
		  'bJQueryUI': true,
		  'sPaginationType': 'full_numbers',
		  "bProcessing": true,
		  "bServerSide": true,
		  "bDestroy":true,
		  "sAjaxSource": '/admin/rest/exchanges',
		  "sAjaxDataProp": "exchanges",
		  "aoColumnDefs": [ 
	           {
	               "fnRender": function ( o, v ) {
	            	   return membrane.createLink('/admin/call', v, [['id',o.aData.id]]);
	               },
	               "aTargets": [ 0 ]
	           },
	           {
	               "fnRender": function ( o, v ) {
	            	   return membrane.createLink('/admin/service-proxy/show', v, [['name',v+':'+o.aData.listenPort]]);
	               },
	               "aTargets": [ 2 ]
	           },
	           {
	               "fnRender": function ( o, v ) {
	            	   return membrane.createLink('/admin/client', v, [['id',v]]);
	               },
	               "aTargets": [ 5 ]
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
    
    $('form').validationEngine('attach', {promptPosition : 'bottomRight', scroll: false});
    $('form').submit(function() {
		return this.validationEngine('validate');
    });			
});
